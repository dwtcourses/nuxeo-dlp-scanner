/*
O * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.platform.scanner.dlp.service;

import java.util.List;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;

/**
 * Redaction provider interface
 * 
 * @since 10.10
 */
public interface RedactionProvider {

    /**
     * Check to see if the service is enabled
     * 
     * @return true if enabled
     */
    boolean isEnabled();

    /**
     * @param blobs the blobs to pass to the API
     * @param features the feature to request from the provider
     * @return {@link Blob} objects
     */
    List<Blob> redact(List<Blob> blobs, List<String> features);

    /**
     * Auto redact a blob input
     * 
     * @param blob the blob
     * @param features the feature to request from the provider
     * @return {@link Blob} redacted
     */
    public Blob redactBlob(Blob blob, List<String> features);

    /**
     * Auto redact a document input
     * 
     * @param blob the blob
     * @param features the feature to request from the provider
     * @return {@link Blob} redacted
     */
    public Blob redactDocument(DocumentModel doc, List<String> features);

}
