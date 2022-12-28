package data;

import vniiem.CRC16_CCITT;
import vniiem.TimeManipulation;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;

// TODO: описываем структуру данных и шлем сюда
public class PackageSin {
    private static final int PACKET_LENGTH = 26;
    private static final int SYNCH = 0x12345678;
    private static final byte[] sycnho = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0x12345678).array();
    private final Integer counter;
    private final LocalDateTime localDateTime;
    private final Double angleSin;
    private final Short crc16;  // unsigned short


    public PackageSin(byte[] dataBytes) throws Exception{
        ByteBuffer data = ByteBuffer.wrap(dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        int synch = data.getInt(0);
        counter = data.getInt(4);
        localDateTime = TimeManipulation.getDateTimeFromUnixUTC(data.getDouble(8));
        angleSin = data.getDouble(16);
        crc16 = data.getShort(data.limit() - 2);
        if (data.limit() != PACKET_LENGTH) {
            throw new Exception("Ошибка в размере пакета");
        } else if ( synch != SYNCH){
            throw new Exception(String.format("Ошибка cинхромаркере: %x", synch) );
        } else if (crc16 != CRC16_CCITT.crc16Ccitt(data, 0, data.limit() - 2)){
            throw new Exception(String.format("Ошибка CRC16: %s - %s", crc16, CRC16_CCITT.crc16Ccitt(data, 0, data.limit() - 2)));
        }
    }

    public static boolean isValidSycnho(byte [] ref){
        return Arrays.equals(ref, sycnho);
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

    public Short getCrc16() {
        return crc16;
    }

    @Override
    public String toString() {
        return String.format("PackageSin: [%s, %s, %s, %s]", counter, localDateTime, angleSin, crc16);
    }
}
