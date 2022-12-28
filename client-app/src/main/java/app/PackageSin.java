package app;

import vniiem.CRC16_CCITT;
import vniiem.TimeManipulation;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDateTime;

// TODO: описываем структуру данных и шлем сюда
public class PackageSin {
    private static final int PACKET_LENGTH = 26;
    private static final int SYNCH = 0x12345678;
    private final Integer counter;
    private final LocalDateTime localDateTime;
    private final Double angleSin;
    private final short crc16;
    private boolean haveErrors;


    public PackageSin(byte[] dataBytes) {
        haveErrors = dataBytes.length != PACKET_LENGTH;

        ByteBuffer data = ByteBuffer.wrap(dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        int synch = data.getInt(0);                 // & 0xffffffffL
        counter = data.getInt(4);
        localDateTime = TimeManipulation.getDateTimeFromUnixUTC(data.getDouble(8));
        angleSin = data.getDouble(16);
        crc16 = data.getShort(data.limit() - 2);   // & 0xffff

        validateField(synch, SYNCH);
        validateField(CRC16_CCITT.crc16Ccitt(data, 0, data.limit() - 2), crc16);
    }

    public void validateField(int val, int ref){
        if (val != ref && !haveErrors) {
            haveErrors = true;
        }
    }
    public void validateField(short val, short ref){
        if (val != ref && !haveErrors) {
            haveErrors = true;
        }
    }

    public static boolean validateSynch(ByteBuffer ref, int pos){
        return ref.getInt(pos) == SYNCH;
    }

    public boolean isHaveErrors() {
        return haveErrors;
    }

    public Integer getCounter() {
        return counter;
    }

    public LocalDateTime getLocalDateTime() {
        return localDateTime;
    }

    public Double getAngleSin() {
        return angleSin;
    }

    public int getCrc16() {
        return crc16;
    }

    @Override
    public String toString() {
        return String.format("PackageSin: [%s, %s, %s, %s]", counter, localDateTime, angleSin, crc16);
    }
}
