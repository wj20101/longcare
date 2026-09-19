package com.ytone.longcare.integration.qlz;

import static com.ytone.longcare.integration.qlz.QlzProtobufCompatibilityTest.assertSample;
import static com.ytone.longcare.integration.qlz.QlzProtobufCompatibilityTest.sample;
import static org.junit.Assert.assertTrue;

import com.comm.util.ProtobufGzipKit;
import com.qiaolz.eco.app.protobuf.RecordDataProto.RecordData;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/** R8 target paths only; the full 13-test public API matrix remains on JVM/Debug. */
public class QlzReleaseProtobufTest {
    private final QlzProtobufCompatibilityTest contracts = new QlzProtobufCompatibilityTest();

    @Test
    public void oldRuntimeFixture() throws Exception {
        contracts.oldRuntimeFixturePreservesAllFields();
    }

    @Test
    public void emptyMessageAndDelimitedBoundaries() throws Exception {
        contracts.defaultMessageHasEmptyCollectionsAndNoPhysiology();
        contracts.delimitedMessagesKeepRecordBoundaries();
    }

    @Test
    public void nestedFieldsAndBuilderIsolation() throws Exception {
        contracts.nestedSamplesAndPhysiologySurviveSerialization();
        contracts.builderCopyOnWriteDoesNotMutatePriorMessages();
    }

    @Test
    public void vendorUploadEncodingAndRepeatedMessages() throws Exception {
        contracts.vendorCompressAndGunzipPreserveMessage();
        contracts.repeatedIndependentRoundTripsDoNotLeakState();
    }

    @Test
    public void malformedMessagesAreRejected() {
        contracts.malformedLengthDelimitedMessageIsRejected();
        contracts.invalidUtf8AndZeroTagAreRejected();
    }

    @Test
    public void vendorGzipBoundariesAndCorruption() throws Exception {
        contracts.vendorRawGzipHandlesEmptyAndLargeInput();
        contracts.vendorGunzipRejectsInvalidTruncatedAndCorruptInput();
    }

    @Test
    public void generatedSdkInputOverloads() throws Exception {
        byte[] bytes = sample().toByteArray();
        assertSample(RecordData.parseFrom(sample().toByteString()));
        assertSample(RecordData.parseFrom(ByteBuffer.wrap(bytes)));
        assertSample(RecordData.parseFrom(new ByteArrayInputStream(bytes)));
        assertSample(RecordData.parseFrom(sample().toByteString().newCodedInput()));
    }

    @Test
    public void unknownFieldSurvivesVendorGzipAndRebuild() throws Exception {
        ByteArrayOutputStream extra = new ByteArrayOutputStream();
        byte[] value = "future-synthetic-field".getBytes(StandardCharsets.UTF_8);
        // Field 1000, wire type 2. No test-only CodedOutputStream factory in the R8 target.
        extra.write(new byte[] {(byte) 0xc2, 0x3e, (byte) value.length});
        extra.write(value);
        byte[] unknownField = extra.toByteArray();
        ByteArrayOutputStream input = new ByteArrayOutputStream();
        input.write(sample().toByteArray());
        input.write(unknownField);
        RecordData parsed = RecordData.parseFrom(input.toByteArray());
        assertSample(parsed);
        byte[] rebuilt = ProtobufGzipKit.gunzip(ProtobufGzipKit.compress(parsed.toBuilder().build()));
        assertSample(RecordData.parseFrom(rebuilt));
        // Search the complete tagged field, without assuming canonical ordering of other fields.
        boolean found = false;
        for (int offset = 0; offset <= rebuilt.length - unknownField.length; offset++) {
            int index = 0;
            while (index < unknownField.length && rebuilt[offset + index] == unknownField[index]) index++;
            if (index == unknownField.length) found = true;
        }
        assertTrue("Unknown field was discarded", found);
    }
}
