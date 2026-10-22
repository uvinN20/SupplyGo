package com.supplygo.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Bounded UTF-8 lines. Callers own and buffer the socket streams. */
public final class FrameIO {
    public static final int MAX_FRAME_BYTES = 8192;

    private FrameIO() { }

    /** Returns null only for EOF between frames; EOF within a frame is invalid. */
    public static String read(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(256);
        int value;
        while ((value = input.read()) != -1) {
            if (value == '\n') {
                byte[] frame = bytes.toByteArray();
                int length = frame.length;
                if (length > 0 && frame[length - 1] == '\r') {
                    length--;
                }
                try {
                    return StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(frame, 0, length)).toString();
                } catch (CharacterCodingException exception) {
                    throw new ProtocolException("INVALID_UTF8", null, "Frame must contain valid UTF-8");
                }
            }
            if (bytes.size() == MAX_FRAME_BYTES) {
                throw new ProtocolException("FRAME_TOO_LARGE", null, "Frame exceeds 8192 bytes");
            }
            bytes.write(value);
        }
        if (bytes.size() != 0) {
            throw new ProtocolException("INCOMPLETE_FRAME", null, "Frame must end with a newline");
        }
        return null;
    }

    public static void write(OutputStream output, String frame) throws IOException {
        byte[] bytes = frame.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FRAME_BYTES || frame.indexOf('\n') >= 0 || frame.indexOf('\r') >= 0) {
            throw new ProtocolException("INVALID_FRAME", null, "Invalid outgoing frame");
        }
        output.write(bytes);
        output.write('\n');
        output.flush();
    }
}
