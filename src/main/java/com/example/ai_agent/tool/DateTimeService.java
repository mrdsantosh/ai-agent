package com.example.ai_agent.tool;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

@Service
public class DateTimeService {

  @Tool(description = """
      Get current date and time in a timezone.

      Parameters:
      - timeZone: optional. IANA id e.g. 'UTC', 'America/New_York', 'Europe/London'.
        If omitted or unknown, the JVM default timezone is used.

      Returns: ISO local date-time (YYYY-MM-DDTHH:MM:SS) in that zone.

      Use when users mention relative dates like "next week" or "tomorrow".
      """)
  public String getCurrentDateTime(String timeZone) {
    ZoneId zone = resolveZone(timeZone);
    return ZonedDateTime.now(zone).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
  }

  private static ZoneId resolveZone(String timeZone) {
    if (timeZone == null || timeZone.isBlank()) {
      return ZoneId.systemDefault();
    }
    String z = timeZone.trim();
    try {
      return ZoneId.of(z);
    } catch (DateTimeException e) {
      return ZoneId.systemDefault();
    }
  }
}
