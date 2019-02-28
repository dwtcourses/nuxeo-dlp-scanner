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
package org.nuxeo.platform.scanner.dlp.operations;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.automation.core.util.StringList;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.blobholder.DocumentBlobHolder;
import org.nuxeo.ecm.core.api.blobholder.SimpleBlobHolder;
import org.nuxeo.platform.scanner.dlp.DataLossPreventionScanner;
import org.nuxeo.platform.scanner.dlp.service.ScanResult;

/**
 * Retrieve a report of sensitive content
 * 
 * @since 10.10
 */
@Operation(id = IdentifySensitiveDataOperation.ID, category = Constants.CAT_BLOB, label = "Identify Sensitive Data", description = "Identify sensitive data within a piece of content.")
public class IdentifySensitiveDataOperation {

    public static final String ID = "Blob.IdentifySensitiveData";

    @Context
    protected DataLossPreventionScanner service;

    @Param(name = "xpath", required = false, values = { "file:content" })
    protected String xpath;

    @Param(name = "infotypes", required = false)
    protected StringList infoTypes;

    @Param(name = "maxfindings", required = false)
    protected Integer maxFindings;

    @OperationMethod
    public Blob run(DocumentModel doc) throws IOException {
        BlobHolder bh = null;
        if (StringUtils.isNotBlank(xpath)) {
            bh = new DocumentBlobHolder(doc, xpath);
        } else {
            bh = doc.getAdapter(BlobHolder.class);
        }
        ScanResult result = runWorker(bh);
        return Blobs.createJSONBlobFromValue(result);
    }

    @OperationMethod
    public Blob run(Blob blob) throws IOException {
        ScanResult result = runWorker(new SimpleBlobHolder(blob));
        return Blobs.createJSONBlobFromValue(result);
    }

    protected ScanResult runWorker(BlobHolder bh) throws IOException {
        return service.identify(bh.getBlob(), infoTypes, maxFindings);
    }

}