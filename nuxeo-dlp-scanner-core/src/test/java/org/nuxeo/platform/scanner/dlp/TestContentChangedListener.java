package org.nuxeo.platform.scanner.dlp;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.EventListenerDescriptor;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.google.inject.Inject;

@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class })
@Deploy({ "org.nuxeo.platform.scanner.dlp.core" })
public class TestContentChangedListener {

    protected final List<String> events = Arrays.asList("documentCreated", "aboutToCreate",
            "beforeDocumentModification");

    @Inject
    protected EventService s;

    @Before
    public void setUp() {
        /*
        String skipCheck = System.getenv("GOOGLE_CREDENTIALS_SET");
        if (skipCheck == null) {
            assertNotNull(System.getenv("GOOGLE_APPLICATION_CREDENTIALS"));
        } */
    }

    @Test
    public void listenerRegistration() {
        EventListenerDescriptor listener = s.getEventListener("dlpListener");
        assertNotNull(listener);
        assertTrue(events.stream().allMatch(listener::acceptEvent));
    }
}
