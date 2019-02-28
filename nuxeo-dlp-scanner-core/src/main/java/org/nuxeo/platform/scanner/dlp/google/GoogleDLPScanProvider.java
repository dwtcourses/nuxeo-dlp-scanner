/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Damon Brown
 */
package org.nuxeo.platform.scanner.dlp.google;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.activation.MimetypesFileTypeMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.blobholder.SimpleBlobHolder;
import org.nuxeo.ecm.core.convert.api.ConversionService;
import org.nuxeo.platform.scanner.dlp.service.RedactionProvider;
import org.nuxeo.platform.scanner.dlp.service.ScanFinding;
import org.nuxeo.platform.scanner.dlp.service.ScanProvider;
import org.nuxeo.platform.scanner.dlp.service.ScanResult;
import org.nuxeo.runtime.api.Framework;

import com.google.cloud.ServiceOptions;
import com.google.cloud.dlp.v2.DlpServiceClient;
import com.google.cloud.dlp.v2.DlpServiceSettings;
import com.google.privacy.dlp.v2.ByteContentItem;
import com.google.privacy.dlp.v2.ContentItem;
import com.google.privacy.dlp.v2.CreateInspectTemplateRequest;
import com.google.privacy.dlp.v2.CustomInfoType;
import com.google.privacy.dlp.v2.Finding;
import com.google.privacy.dlp.v2.InfoType;
import com.google.privacy.dlp.v2.InfoTypeDescription;
import com.google.privacy.dlp.v2.InspectConfig;
import com.google.privacy.dlp.v2.InspectConfig.FindingLimits;
import com.google.privacy.dlp.v2.InspectContentRequest;
import com.google.privacy.dlp.v2.InspectContentResponse;
import com.google.privacy.dlp.v2.InspectResult;
import com.google.privacy.dlp.v2.InspectTemplate;
import com.google.privacy.dlp.v2.Likelihood;
import com.google.privacy.dlp.v2.ListInfoTypesRequest;
import com.google.privacy.dlp.v2.ListInfoTypesResponse;
import com.google.privacy.dlp.v2.ProjectName;
import com.google.privacy.dlp.v2.RedactImageRequest;
import com.google.privacy.dlp.v2.RedactImageResponse;
import com.google.protobuf.ByteString;

/**
 * @author dbrown
 */
public class GoogleDLPScanProvider implements RedactionProvider, ScanProvider, GoogleDLPConstants {

    protected static final Log log = LogFactory.getLog(GoogleDLPScanProvider.class);

    Likelihood likelihood = Likelihood.LIKELY;

    Likelihood sensitivity = Likelihood.LIKELY;

    private int maxFindings = 5;

    private boolean enabled = true;

    private boolean convertToText = true;

    private boolean includeQuote = true;

    private String projectId = null;

    private DlpServiceSettings dlpServiceSettings = null;

    private List<InfoType> infoTypesList = null;

    private List<CustomInfoType> customInfoTypesList = null;

    public GoogleDLPScanProvider() {
        super();
        init();
    }

    private void init() {
        try {
            // Load credentials
            dlpServiceSettings = DlpServiceSettings.newBuilder().build();
        } catch (IOException iox) {
            log.error("Error loading Google Authentication Credentials", iox);
            log.error("Data Loss Prevention scanning will be unavailable");
        }

        // Load Project ID
        projectId = Framework.getProperty(API_PROJECT, ServiceOptions.getDefaultProjectId());

        enabled = Boolean.parseBoolean(Framework.getProperty(ENABLED, "true"));
        includeQuote = Boolean.parseBoolean(Framework.getProperty(INCLUDE_QUOTE, "true"));
        convertToText = Boolean.parseBoolean(Framework.getProperty(CONVERT_TO_TEXT, "true"));

        // Load Likelihood
        String likelihoodStr = Framework.getProperty(LIKELYHOOD, DEFAULT_LIKELYHOOD);
        if (StringUtils.isNotBlank(likelihoodStr)) {
            try {
                likelihood = Likelihood.valueOf(likelihoodStr);
            } catch (NumberFormatException nfe) {
                log.error(LIKELYHOOD + " is not one of: " + Arrays.asList(Likelihood.values()));
            }
        }

        // Load Sensitivity
        String sensitivityStr = Framework.getProperty(SENSITIVITY, DEFAULT_LIKELYHOOD);
        if (StringUtils.isNotBlank(likelihoodStr)) {
            try {
                sensitivity = Likelihood.valueOf(sensitivityStr);
            } catch (NumberFormatException nfe) {
                log.error(SENSITIVITY + " is not one of: " + Arrays.asList(Likelihood.values()));
            }
        }

        // Load max findings
        String maxFindingsStr = Framework.getProperty(MAX_FINDINGS, DEFAULT_MAX_FINDINGS);
        if (StringUtils.isNotBlank(maxFindingsStr)) {
            try {
                maxFindings = Integer.parseInt(maxFindingsStr);
            } catch (NumberFormatException nfe) {
                log.error(MAX_FINDINGS + " is not a number");
            }
        }

        // Load info types
        String infoTypeStr = Framework.getProperty(INFOTYPES, DEFAULT_INFOTYPES);
        if (StringUtils.isNotBlank(infoTypeStr)) {
            infoTypesList = new ArrayList<>();
            String[] infoTypes = infoTypeStr.split(",");
            for (String infoType : infoTypes) {
                infoTypesList.add(InfoType.newBuilder().setName(infoType).build());
            }
        } else {
            infoTypesList = Collections.emptyList();
        }

        // Load custom info types
        customInfoTypesList = Collections.emptyList();
    }

    @Override
    public boolean supportsRedaction() {
        return true;
    }

    /*
     * (non-Javadoc)
     * @see org.nuxeo.platform.scanner.dlp.service.ScanProvider#isEnabled()
     */
    @Override
    public boolean isEnabled() {
        return enabled && dlpServiceSettings != null;
    }

    @Override
    public boolean checkBlobs(List<Blob> blobs) {
        // TODO Auto-generated method stub
        return true;
    }

    @Override
    public List<ScanResult> identify(List<Blob> blobs, List<String> features, Integer maxResults) throws IOException {
        List<ScanResult> results = new LinkedList<>();
        for (Blob b : blobs) {
            results.add(scanBlob(b, features, maxResults));
        }
        return results;
    }

    @Override
    public List<Blob> redact(List<Blob> blobs, List<String> features) {
        List<Blob> results = new LinkedList<>();
        for (Blob b : blobs) {
            results.add(redactBlob(b, features));
        }
        return results;
    }

    protected ScanResult scanBlob(Blob blob, List<String> features, Integer maxResults) {
        if (!isEnabled()) {
            return ScanResult.makeFailed();
        }

        if (blob != null && (blob.getLength() == -1 || blob.getLength() > 0)) {
            return inspectBlob(blob, features, maxResults);
        }

        return new ScanResult(Collections.emptyList());
    }

    /**
     * Inspect a local file
     *
     * @param filePath The path to a local file to inspect. Can be a text, JPG, or PNG file.
     * @param minLikelihood The minimum likelihood required before returning a match
     * @param maxFindings The maximum number of findings to report (0 = server maximum)
     * @param infoTypes The infoTypes of information to match
     * @param includeQuote Whether to include the matching string
     * @param projectId Google Cloud project ID
     */
    protected ScanResult inspectBlob(Blob blob, List<String> features, Integer maxResults) {

        // Instantiates a client
        List<ScanFinding> findings = new LinkedList<>();
        try (DlpServiceClient dlpServiceClient = DlpServiceClient.create(dlpServiceSettings)) {
            // detect file mime type, default to application/octet-stream
            String mimeType = blob.getMimeType();
            if (mimeType == null) {
                mimeType = MimetypesFileTypeMap.getDefaultFileTypeMap().getContentType(blob.getFile());
            }

            if (convertToText
                    && (mimeType == null || (!mimeType.startsWith("text") && !mimeType.startsWith("image")))) {
                ConversionService conv = Framework.getService(ConversionService.class);
                BlobHolder text = conv.convertToMimeType("text/plain", new SimpleBlobHolder(blob),
                        Collections.emptyMap());
                blob = text.getBlob();
            }

            ByteContentItem.BytesType bytesType;
            switch (mimeType) {
            case "image/jpeg":
                bytesType = ByteContentItem.BytesType.IMAGE_JPEG;
                break;
            case "image/bmp":
                bytesType = ByteContentItem.BytesType.IMAGE_BMP;
                break;
            case "image/png":
                bytesType = ByteContentItem.BytesType.IMAGE_PNG;
                break;
            case "image/svg":
                bytesType = ByteContentItem.BytesType.IMAGE_SVG;
                break;
            default:
                bytesType = ByteContentItem.BytesType.BYTES_TYPE_UNSPECIFIED;
                break;
            }

            // Override info types
            List<InfoType> infoTypes = infoTypesList;
            if (features != null && !features.isEmpty()) {
                infoTypes = new LinkedList<>();
                for (String infoType : features) {
                    infoTypes.add(InfoType.newBuilder().setName(infoType).build());
                }
            }

            ByteString bytes = ByteString.readFrom(blob.getStream());
            if (bytes.size() == 0) {
                return new ScanResult(Collections.emptyList());
            }
            ByteContentItem byteContentItem = ByteContentItem.newBuilder().setType(bytesType).setData(bytes).build();
            ContentItem contentItem = ContentItem.newBuilder().setByteItem(byteContentItem).build();

            FindingLimits findingLimits = FindingLimits.newBuilder()
                                                       .setMaxFindingsPerRequest(
                                                               maxResults != null ? maxResults : maxFindings)
                                                       .build();

            InspectConfig inspectConfig = InspectConfig.newBuilder()
                                                       .addAllInfoTypes(infoTypes)
                                                       .addAllCustomInfoTypes(customInfoTypesList)
                                                       .setMinLikelihood(likelihood)
                                                       .setLimits(findingLimits)
                                                       .setIncludeQuote(includeQuote)
                                                       .build();

            InspectContentRequest request = InspectContentRequest.newBuilder()
                                                                 .setParent(ProjectName.of(projectId).toString())
                                                                 .setInspectConfig(inspectConfig)
                                                                 .setItem(contentItem)
                                                                 .build();

            InspectContentResponse response = dlpServiceClient.inspectContent(request);

            InspectResult result = response.getResult();
            if (result.getFindingsCount() > 0) {
                for (Finding finding : result.getFindingsList()) {
                    String quote = null;
                    if (includeQuote) {
                        quote = finding.getQuote();
                    }
                    String type = finding.getInfoType().getName();
                    Likelihood score = finding.getLikelihood();
                    boolean sensitive = score.ordinal() >= sensitivity.ordinal();
                    findings.add(new ScanFinding(sensitive, score.toString(), type, quote));
                }

            }
            return new ScanResult(findings);
        } catch (Exception e) {
            log.error("Error calling Google Data Loss Prevention Service", e);
            return ScanResult.makeFailed();
        }
    }

    public Blob redactBlob(Blob blob) {
        return redactBlob(blob, null);
    }

    public Blob redactBlob(Blob blob, List<String> features) {
        // String projectId = "my-project-id";
        // String filePath = "path/to/image.png";

        // Initialize client that will be used to send requests. This client only needs to be created
        // once, and can be reused for multiple requests. After completing all of your requests, call
        // the "close" method on the client to safely clean up any remaining background resources.
        try (DlpServiceClient dlp = DlpServiceClient.create()) {
            // Specify the project used for request.
            ProjectName project = ProjectName.of(projectId);

            String mimeType = blob.getMimeType();
            if (mimeType == null) {
                mimeType = MimetypesFileTypeMap.getDefaultFileTypeMap().getContentType(blob.getFile());
            }

            ByteContentItem.BytesType bytesType;
            switch (mimeType) {
            case "image/jpeg":
                bytesType = ByteContentItem.BytesType.IMAGE_JPEG;
                break;
            case "image/bmp":
                bytesType = ByteContentItem.BytesType.IMAGE_BMP;
                break;
            case "image/png":
                bytesType = ByteContentItem.BytesType.IMAGE_PNG;
                break;
            case "image/svg":
                bytesType = ByteContentItem.BytesType.IMAGE_SVG;
                break;
            default:
                bytesType = ByteContentItem.BytesType.BYTES_TYPE_UNSPECIFIED;
                break;
            }

            // Specify the content to be inspected.
            ByteString fileBytes = ByteString.readFrom(blob.getStream());
            ByteContentItem byteItem = ByteContentItem.newBuilder().setType(bytesType).setData(fileBytes).build();

            // Override info types
            List<InfoType> infoTypes = infoTypesList;
            if (features != null && !features.isEmpty()) {
                infoTypes = new LinkedList<>();
                for (String infoType : features) {
                    infoTypes.add(InfoType.newBuilder().setName(infoType).build());
                }
            }

            InspectConfig config = InspectConfig.newBuilder()
                                                .addAllInfoTypes(infoTypes)
                                                .addAllCustomInfoTypes(customInfoTypesList)
                                                .setMinLikelihood(likelihood)
                                                .build();

            // Construct the Redact request to be sent by the client.
            RedactImageRequest request = RedactImageRequest.newBuilder()
                                                           .setParent(project.toString())
                                                           .setByteItem(byteItem)
                                                           .setInspectConfig(config)
                                                           .build();

            // Use the client to send the API request.
            RedactImageResponse response = dlp.redactImage(request);

            // Parse the response and process results.
            Blob redacted = Blobs.createBlob(response.getRedactedImage().newInput());
            redacted.setFilename("redacted.png");
            redacted.setMimeType("image/png");
            return redacted;
        } catch (Exception e) {
            throw new NuxeoException("Unable to redact image", e);
        }
    }

    /**
     * Create a new DLP inspection configuration template.
     *
     * @param displayName (Optional) The human-readable name to give the template
     * @param projectId Google Cloud Project ID to call the API under
     * @param templateId (Optional) The name of the template to be created
     * @param infoTypeList The infoTypes of information to match
     * @param minLikelihood The minimum likelihood required before returning a match
     * @param maxFindings The maximum number of findings to report per request (0 = server maximum)
     */
    protected void createInspectTemplate(String displayName, String templateId, String description) {
        try (DlpServiceClient dlpServiceClient = DlpServiceClient.create()) {

            FindingLimits findingLimits = FindingLimits.newBuilder().setMaxFindingsPerRequest(maxFindings).build();

            // Construct the inspection configuration for the template
            InspectConfig config = InspectConfig.newBuilder()
                                                .addAllInfoTypes(infoTypesList)
                                                .setMinLikelihood(likelihood)
                                                .setLimits(findingLimits)
                                                .build();

            InspectTemplate template = InspectTemplate.newBuilder()
                                                      .setInspectConfig(config)
                                                      .setDisplayName(displayName)
                                                      .setDescription(description)
                                                      .build();

            CreateInspectTemplateRequest request = CreateInspectTemplateRequest.newBuilder()
                                                                               .setParent(ProjectName.of(projectId)
                                                                                                     .toString())
                                                                               .setInspectTemplate(template)
                                                                               .setTemplateId(templateId)
                                                                               .build();

            InspectTemplate response = dlpServiceClient.createInspectTemplate(request);
            System.out.printf("Template created: %s", response.getName());
        } catch (Exception e) {
            System.out.printf("Error creating template: %s", e.getMessage());
        }
    }

    /**
     * List the types of sensitive information the DLP API supports. Ref:
     * https://cloud.google.com/dlp/docs/infotypes-reference
     *
     * @param filter The filter to use, e.g. "supported_by=INSPECT"
     * @param languageCode The BCP-47 language code to use, e.g. 'en-US'
     * @throws IOException
     */
    public List<InfoTypeDescription> getInfoTypes(String filter, String languageCode) throws IOException {
        try (DlpServiceClient dlpClient = DlpServiceClient.create()) {
            ListInfoTypesRequest.Builder builder = ListInfoTypesRequest.newBuilder();
            if (filter != null) {
                builder.setFilter(filter);
            }
            if (languageCode != null) {
                builder.setLanguageCode(languageCode);
            }

            ListInfoTypesRequest request = builder.build();
            ListInfoTypesResponse infoTypesResponse = dlpClient.listInfoTypes(request);
            return infoTypesResponse.getInfoTypesList();
        }
    }

}
