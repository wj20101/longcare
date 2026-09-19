package com.ytone.longcare.integration.qlz;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.comm.util.ProtobufGzipKit;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import com.qiaolz.eco.app.protobuf.RecordDataProto.RecordData;
import com.qiaolz.eco.app.protobuf.RecordDataProto.RecordData.AssessedData;
import com.qiaolz.eco.app.protobuf.RecordDataProto.RecordData.AssessedData.FingerSample;
import com.qiaolz.eco.app.protobuf.RecordDataProto.RecordData.Physiology;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.Test;

/** Real QLZ 1.3.0.5 message/Gzip code only: no SDK initialization, network, BLE or user data. */
public class QlzProtobufCompatibilityTest {
    // Generated from sample() with the unchanged QLZ 1.3.0.5 AAR + protobuf-javalite 4.28.3.
    // Assert field semantics, not canonical encoding or identical compressed bytes.
    private static final String OLD_RUNTIME_FIXTURE =
            "0a0e73796e7468657469632d75736572121073796e7468657469632d7265636f7264"
            + "1a0ce59088e68890e8aebee5a487220d73796e7468657469632d6d61632a0130320130"
            + "3a10e6b58be8af95e59cb0e59d80f09f9982450000a03f4a0f73796e7468657469632d6f72646572"
            + "520fe585bce5aeb9e680a7e6b58be8af9558026003"
            + "6a2e0a066974656d2d3010011a0a080110021803200428051a0a080b100c180d200e280f1a0a08151016181720182819"
            + "6a2e0a066974656d2d3110021a0a086510661867206828691a0a086f10701871207228731a0a0879107a187b207c287d"
            + "7204746573747a0d73796e7468657469632d706964"
            + "820124090000000000404240110000000000005240190000000000805840210000000000204540";

    @Test
    public void oldRuntimeFixturePreservesAllFields() throws Exception {
        byte[] bytes = new byte[OLD_RUNTIME_FIXTURE.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(OLD_RUNTIME_FIXTURE.substring(i * 2, i * 2 + 2), 16);
        }
        assertSample(RecordData.parseFrom(bytes));
    }

    @Test
    public void defaultMessageHasEmptyCollectionsAndNoPhysiology() throws Exception {
        RecordData empty = RecordData.parseFrom(new byte[0]);
        assertEquals(RecordData.getDefaultInstance(), empty);
        assertEquals("", empty.getUserid());
        assertEquals(0, empty.getAdatalistCount());
        assertFalse(empty.hasPhysiology());
        assertArrayEquals(new byte[0], empty.toByteArray());
    }

    @Test
    public void nestedSamplesAndPhysiologySurviveSerialization() throws Exception {
        assertSample(RecordData.parseFrom(sample().toByteArray()));
    }

    @Test
    public void allSupportedInputFormsPreserveFields() throws Exception {
        byte[] bytes = sample().toByteArray();
        assertSample(RecordData.parseFrom(ByteString.copyFrom(bytes)));
        assertSample(RecordData.parseFrom(ByteBuffer.wrap(bytes)));
        assertSample(RecordData.parseFrom(new ByteArrayInputStream(bytes)));
        assertSample(RecordData.parseFrom(CodedInputStream.newInstance(bytes)));
        assertSample(RecordData.parser().parseFrom(bytes));
    }

    @Test
    public void delimitedMessagesKeepRecordBoundaries() throws Exception {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        sample().writeDelimitedTo(stream);
        RecordData.getDefaultInstance().writeDelimitedTo(stream);
        ByteArrayInputStream input = new ByteArrayInputStream(stream.toByteArray());
        assertSample(RecordData.parseDelimitedFrom(input));
        assertEquals(RecordData.getDefaultInstance(), RecordData.parseDelimitedFrom(input));
        assertEquals(null, RecordData.parseDelimitedFrom(input));
    }

    @Test
    public void builderCopyOnWriteDoesNotMutatePriorMessages() {
        RecordData.Builder builder = sample().toBuilder();
        RecordData before = builder.build();
        RecordData after = builder.setUserid("other-synthetic-user").clearAdatalist().build();
        assertSample(before);
        assertEquals(0, after.getAdatalistCount());
        assertEquals("other-synthetic-user", after.getUserid());
        assertNotEquals(before, after);
        assertSample(sample());
    }

    @Test
    public void unknownFieldSurvivesParseRebuildAndGzip() throws Exception {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        stream.write(sample().toByteArray());
        CodedOutputStream output = CodedOutputStream.newInstance(stream);
        output.writeString(1000, "future-synthetic-field");
        output.flush();
        RecordData parsed = RecordData.parseFrom(stream.toByteArray());
        assertSample(parsed);
        byte[] rebuilt = ProtobufGzipKit.gunzip(ProtobufGzipKit.compress(parsed.toBuilder().build()));
        CodedInputStream input = CodedInputStream.newInstance(rebuilt);
        boolean found = false;
        for (int tag = input.readTag(); tag != 0; tag = input.readTag()) {
            if (tag == (1000 << 3 | 2)) {
                assertEquals("future-synthetic-field", input.readStringRequireUtf8());
                found = true;
            } else {
                assertTrue(input.skipField(tag));
            }
        }
        assertTrue("Unknown field was discarded", found);
        assertSample(RecordData.parseFrom(rebuilt));
    }

    @Test
    public void malformedLengthDelimitedMessageIsRejected() {
        assertThrows(InvalidProtocolBufferException.class,
                () -> RecordData.parseFrom(new byte[] {0x0a, 0x05, 0x41}));
    }

    @Test
    public void invalidUtf8AndZeroTagAreRejected() {
        assertThrows(InvalidProtocolBufferException.class,
                () -> RecordData.parseFrom(new byte[] {0x0a, 0x01, (byte) 0xff}));
        assertThrows(InvalidProtocolBufferException.class,
                () -> RecordData.parseFrom(new byte[] {0}));
    }

    @Test
    public void vendorCompressAndGunzipPreserveMessage() throws Exception {
        byte[] compressed = ProtobufGzipKit.compress(sample());
        assertEquals(0x1f, compressed[0] & 0xff);
        assertEquals(0x8b, compressed[1] & 0xff);
        assertSample(RecordData.parseFrom(ProtobufGzipKit.gunzip(compressed)));
    }

    @Test
    public void vendorRawGzipHandlesEmptyAndLargeInput() throws Exception {
        assertArrayEquals(new byte[0], ProtobufGzipKit.gunzip(ProtobufGzipKit.gzip(new byte[0])));
        byte[] input = new byte[20_000];
        for (int i = 0; i < input.length; i++) input[i] = (byte) (i % 251);
        assertArrayEquals(input, ProtobufGzipKit.gunzip(ProtobufGzipKit.gzip(input)));
    }

    @Test
    public void vendorGunzipRejectsInvalidTruncatedAndCorruptInput() throws Exception {
        assertThrows(IOException.class, () -> ProtobufGzipKit.gunzip(new byte[] {1, 2, 3}));
        byte[] compressed = ProtobufGzipKit.compress(sample());
        assertThrows(IOException.class,
                () -> ProtobufGzipKit.gunzip(Arrays.copyOf(compressed, compressed.length - 5)));
        compressed[compressed.length - 8] ^= 1; // Corrupt CRC, not a protobuf field.
        assertThrows(IOException.class, () -> ProtobufGzipKit.gunzip(compressed));
    }

    @Test
    public void repeatedIndependentRoundTripsDoNotLeakState() throws Exception {
        for (int i = 0; i < 30; i++) {
            RecordData message = sample().toBuilder().setRecordid("synthetic-record-" + i).build();
            RecordData parsed = RecordData.parseFrom(ProtobufGzipKit.gunzip(ProtobufGzipKit.compress(message)));
            assertEquals(message, parsed);
            assertEquals(message.hashCode(), parsed.hashCode());
        }
        assertSample(sample());
    }

    // The same synthetic record is used for cross-runtime fixtures. No customer/device identifiers.
    public static RecordData sample() {
        RecordData.Builder record = RecordData.newBuilder()
                .setUserid("synthetic-user").setRecordid("synthetic-record")
                .setDevicename("合成设备").setDevicemac("synthetic-mac")
                .setLat("0").setLon("0").setAddrdetail("测试地址🙂")
                .setFee(1.25f).setOrderid("synthetic-order").setRemark("兼容性测试")
                .setCtype(2).setUtype(3).setOrderType("test").setPid("synthetic-pid")
                .setPhysiology(Physiology.newBuilder().setTemperature(36.5).setHeartRate(72)
                        .setBloodOxygen(98).setHrv(42.25));
        for (int item = 0; item < 2; item++) {
            AssessedData.Builder data = AssessedData.newBuilder().setItemid("item-" + item).setItemtype(item + 1);
            for (int index = 0; index < 3; index++) {
                int value = item * 100 + index * 10;
                data.addSamples(FingerSample.newBuilder().setOne(value + 1).setTwo(value + 2)
                        .setThree(value + 3).setFour(value + 4).setFive(value + 5));
            }
            record.addAdatalist(data);
        }
        return record.build();
    }

    public static void assertSample(RecordData record) {
        assertEquals("synthetic-user", record.getUserid());
        assertEquals("synthetic-record", record.getRecordid());
        assertEquals("合成设备", record.getDevicename());
        assertEquals("synthetic-mac", record.getDevicemac());
        assertEquals("0", record.getLat());
        assertEquals("0", record.getLon());
        assertEquals("测试地址🙂", record.getAddrdetail());
        assertEquals(1.25f, record.getFee(), 0f);
        assertEquals("synthetic-order", record.getOrderid());
        assertEquals("兼容性测试", record.getRemark());
        assertEquals(2, record.getCtype());
        assertEquals(3, record.getUtype());
        assertEquals("test", record.getOrderType());
        assertEquals("synthetic-pid", record.getPid());
        assertTrue(record.hasPhysiology());
        assertEquals(36.5, record.getPhysiology().getTemperature(), 0);
        assertEquals(72, record.getPhysiology().getHeartRate(), 0);
        assertEquals(98, record.getPhysiology().getBloodOxygen(), 0);
        assertEquals(42.25, record.getPhysiology().getHrv(), 0);
        assertEquals(2, record.getAdatalistCount());
        for (int item = 0; item < 2; item++) {
            AssessedData data = record.getAdatalist(item);
            assertEquals("item-" + item, data.getItemid());
            assertEquals(item + 1, data.getItemtype());
            assertEquals(3, data.getSamplesCount());
            for (int index = 0; index < 3; index++) {
                FingerSample finger = data.getSamples(index);
                int value = item * 100 + index * 10;
                assertEquals(value + 1, finger.getOne());
                assertEquals(value + 2, finger.getTwo());
                assertEquals(value + 3, finger.getThree());
                assertEquals(value + 4, finger.getFour());
                assertEquals(value + 5, finger.getFive());
            }
        }
    }
}
