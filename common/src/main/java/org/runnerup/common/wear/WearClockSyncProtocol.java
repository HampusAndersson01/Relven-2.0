package org.runnerup.common.wear;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

/** Small NTP-style Wear clock sample: watch and phone timestamp monotonic and wall clocks. */
public final class WearClockSyncProtocol {
  private static final int MAGIC = 0x52574331; // RWC1
  private static final int VERSION = 2;
  private static final int LEGACY_VERSION = 1;
  private static final int REQUEST = 1;
  private static final int REPLY = 2;
  private static final int MAX_BYTES = 128;

  private WearClockSyncProtocol() {}

  public static byte[] encodeRequest(String requestId, long watchSentAt) throws IOException {
    return encodeRequest(requestId, watchSentAt, 0L);
  }

  public static byte[] encodeRequest(String requestId, long watchSentAt, long watchWallTime)
      throws IOException {
    validateRequest(requestId, watchSentAt);
    if (watchWallTime < 0L) throw new IOException("Invalid watch wall-clock sample");
    return encode(REQUEST, requestId, watchSentAt, watchWallTime, 0L, 0L, 0L, 0L);
  }

  public static Request decodeRequest(byte[] payload) throws IOException {
    Message message = decode(payload, REQUEST);
    validateRequest(message.requestId, message.watchSentAtElapsedRealtimeMs);
    return new Request(
        message.requestId, message.watchSentAtElapsedRealtimeMs, message.watchSentAtWallTimeMillis);
  }

  public static byte[] encodeReply(
      String requestId, long watchSentAt, long phoneReceivedAt, long phoneSentAt)
      throws IOException {
    return encodeReply(requestId, watchSentAt, 0L, phoneReceivedAt, phoneSentAt, 0L, 0L);
  }

  public static byte[] encodeReply(
      String requestId,
      long watchSentAt,
      long watchSentWallTime,
      long phoneReceivedAt,
      long phoneSentAt,
      long phoneReceivedWallTime,
      long phoneSentWallTime)
      throws IOException {
    validateRequest(requestId, watchSentAt);
    if (watchSentWallTime < 0L
        || phoneReceivedAt < 0L
        || phoneSentAt < phoneReceivedAt
        || phoneReceivedWallTime < 0L
        || phoneSentWallTime < phoneReceivedWallTime) {
      throw new IOException("Invalid phone clock sample");
    }
    return encode(
        REPLY,
        requestId,
        watchSentAt,
        watchSentWallTime,
        phoneReceivedAt,
        phoneReceivedWallTime,
        phoneSentAt,
        phoneSentWallTime);
  }

  public static Reply decodeReply(byte[] payload) throws IOException {
    Message message = decode(payload, REPLY);
    validateRequest(message.requestId, message.watchSentAtElapsedRealtimeMs);
    if (message.phoneReceivedAtElapsedRealtimeMs < 0
        || message.phoneSentAtElapsedRealtimeMs < message.phoneReceivedAtElapsedRealtimeMs
        || message.phoneReceivedAtWallTimeMillis < 0
        || message.phoneSentAtWallTimeMillis < message.phoneReceivedAtWallTimeMillis) {
      throw new IOException("Invalid phone clock sample");
    }
    return new Reply(message);
  }

  private static byte[] encode(
      int type,
      String id,
      long watchElapsed,
      long watchWall,
      long phoneReceivedElapsed,
      long phoneReceivedWall,
      long phoneSentElapsed,
      long phoneSentWall)
      throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream output = new DataOutputStream(bytes)) {
      output.writeInt(MAGIC);
      output.writeByte(VERSION);
      output.writeByte(type);
      output.writeUTF(id);
      output.writeLong(watchElapsed);
      if (type == REQUEST) {
        output.writeLong(watchWall);
      } else {
        output.writeLong(watchWall);
        output.writeLong(phoneReceivedElapsed);
        output.writeLong(phoneReceivedWall);
        output.writeLong(phoneSentElapsed);
        output.writeLong(phoneSentWall);
      }
    }
    if (bytes.size() > MAX_BYTES) throw new IOException("Clock message too large");
    return bytes.toByteArray();
  }

  private static Message decode(byte[] payload, int expectedType) throws IOException {
    if (payload == null || payload.length < 19 || payload.length > MAX_BYTES) {
      throw new IOException("Invalid clock message size");
    }
    try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
      if (input.readInt() != MAGIC) throw new IOException("Invalid clock message header");
      int version = input.readUnsignedByte();
      if (version != LEGACY_VERSION && version != VERSION) {
        throw new IOException("Unsupported clock version");
      }
      if (input.readUnsignedByte() != expectedType)
        throw new IOException("Unexpected clock message type");
      String id = input.readUTF();
      long watchElapsed = input.readLong();
      long watchWall = version >= VERSION ? input.readLong() : 0L;
      long phoneReceivedElapsed = expectedType == REPLY ? input.readLong() : 0L;
      long phoneReceivedWall = expectedType == REPLY && version >= VERSION ? input.readLong() : 0L;
      long phoneSentElapsed = expectedType == REPLY ? input.readLong() : 0L;
      long phoneSentWall = expectedType == REPLY && version >= VERSION ? input.readLong() : 0L;
      if (input.available() != 0) throw new IOException("Trailing clock message data");
      return new Message(
          id,
          watchElapsed,
          watchWall,
          phoneReceivedElapsed,
          phoneReceivedWall,
          phoneSentElapsed,
          phoneSentWall);
    }
  }

  private static void validateRequest(String requestId, long timestamp) throws IOException {
    try {
      if (requestId == null || !UUID.fromString(requestId).toString().equals(requestId)) {
        throw new IOException("Invalid clock request ID");
      }
    } catch (IllegalArgumentException invalid) {
      throw new IOException("Invalid clock request ID", invalid);
    }
    if (timestamp < 0) throw new IOException("Invalid clock timestamp");
  }

  private static final class Message {
    final String requestId;
    final long watchSentAtElapsedRealtimeMs;
    final long watchSentAtWallTimeMillis;
    final long phoneReceivedAtElapsedRealtimeMs;
    final long phoneReceivedAtWallTimeMillis;
    final long phoneSentAtElapsedRealtimeMs;
    final long phoneSentAtWallTimeMillis;

    Message(
        String requestId,
        long watchElapsed,
        long watchWall,
        long phoneReceivedElapsed,
        long phoneReceivedWall,
        long phoneSentElapsed,
        long phoneSentWall) {
      this.requestId = requestId;
      this.watchSentAtElapsedRealtimeMs = watchElapsed;
      this.watchSentAtWallTimeMillis = watchWall;
      this.phoneReceivedAtElapsedRealtimeMs = phoneReceivedElapsed;
      this.phoneReceivedAtWallTimeMillis = phoneReceivedWall;
      this.phoneSentAtElapsedRealtimeMs = phoneSentElapsed;
      this.phoneSentAtWallTimeMillis = phoneSentWall;
    }
  }

  public static final class Request {
    private final String requestId;
    private final long watchSentAtElapsedRealtimeMs;
    private final long watchSentAtWallTimeMillis;

    private Request(String requestId, long elapsed, long wallTime) {
      this.requestId = requestId;
      this.watchSentAtElapsedRealtimeMs = elapsed;
      this.watchSentAtWallTimeMillis = wallTime;
    }

    public String getRequestId() {
      return requestId;
    }

    public long getWatchSentAtElapsedRealtimeMs() {
      return watchSentAtElapsedRealtimeMs;
    }

    public long getWatchSentAtWallTimeMillis() {
      return watchSentAtWallTimeMillis;
    }
  }

  public static final class Reply {
    private final Message message;

    private Reply(Message message) {
      this.message = message;
    }

    public String getRequestId() {
      return message.requestId;
    }

    public long getWatchSentAtElapsedRealtimeMs() {
      return message.watchSentAtElapsedRealtimeMs;
    }

    public long getWatchSentAtWallTimeMillis() {
      return message.watchSentAtWallTimeMillis;
    }

    public long getPhoneReceivedAtElapsedRealtimeMs() {
      return message.phoneReceivedAtElapsedRealtimeMs;
    }

    public long getPhoneReceivedAtWallTimeMillis() {
      return message.phoneReceivedAtWallTimeMillis;
    }

    public long getPhoneSentAtElapsedRealtimeMs() {
      return message.phoneSentAtElapsedRealtimeMs;
    }

    public long getPhoneSentAtWallTimeMillis() {
      return message.phoneSentAtWallTimeMillis;
    }
  }
}
