package vniiem;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class TimeManipulation {
	public static int UNIX_EPOCH_1_JAN_2000 = 946684800;
	
	private static final ZoneId utcZone = ZoneId.ofOffset("UTC", ZoneOffset.ofHours(0));
	public static double getUnixTimeUTC(LocalDateTime dt)
	{
		ZonedDateTime dtatz =  dt.atZone(utcZone);
		return dtatz.toEpochSecond()+((dtatz.getNano()/1000000.0)/1000.0);
	}

	public static LocalDateTime getDateTimeFromUnixUTC(double unix_utc)
	{
		long millis = (long)(unix_utc*1000.0);
		return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.ofOffset("UTC", ZoneOffset.ofHours(0)));
	}


	/**
	 * Convert Microsoft un OLE Automation - OADate to Java Date.
	 * @param d - OADate
	 * @return date
	 */
	public static LocalDateTime convertFromOADate(double d)  {
		double  mantissa = d - (long) d;
		double hour = mantissa*24;
		double min =(hour - (long)hour) * 60;
		double sec=(min- (long)min) * 60;

		LocalDateTime dt = LocalDateTime.of(1899, 12, 30, 0, 0);
		dt =  dt.plusDays((long)d);
		dt = dt.plusHours((long)hour);
		dt = dt.plusMinutes((long)min);
		dt = dt.plusSeconds((long)sec);

		return dt;
	}
	public static String getFormatStringLongDateTime()
	{
		return "ddMMyy HH:mm:ss";
	}
	public static String ToLongTimeString(LocalDateTime dt)
	{
		String result = "";
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");
		result =  dt.format(formatter);
		return result;
	}

	
	public static String ToLongTimeStringMillis(LocalDateTime dt)
	{
		String result = "";
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSS");
		result =  dt.format(formatter);
		return result;
	}
	public static String ToShortTimeString(LocalDateTime dt)
	{
		String result = "";
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
		result =  dt.format(formatter);
		return result;

	}
	
	public static String ToStandartDateTimeString(LocalDateTime dt)
	{
		String result = "";
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern(TimeManipulation.getFormatStringLongDateTime());
		result =  dt.format(formatter);
		return result;

	}
	
	public static LocalDateTime convertToLocalDateTimeViaInstant(Date dateToConvert) {
	    return dateToConvert.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
	    }
	
	public static Date convertToDate(LocalDateTime dt)
	{
		Date out = Date.from(dt.atZone(ZoneId.systemDefault()).toInstant());
		return out;
	}
}
