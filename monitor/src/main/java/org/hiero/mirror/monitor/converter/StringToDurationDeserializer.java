// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.converter;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

public class StringToDurationDeserializer extends StdDeserializer<Duration> {

    private static final Pattern PATTERN = Pattern.compile("(\\d+d)?(\\d+h)?(\\d+m)?(\\d+s)?");
    private static final long serialVersionUID = 3690958538780466689L;

    protected StringToDurationDeserializer() {
        super(Duration.class);
    }

    @Override
    public Duration deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String text = p.getValueAsString();
        if (StringUtils.isBlank(text)) {
            return null;
        }

        Matcher matcher = PATTERN.matcher(text);

        if (matcher.matches() && matcher.groupCount() > 0) {
            Duration duration = Duration.ZERO;
            String days = matcher.group(1);
            if (StringUtils.isNotBlank(days)) {
                duration = duration.plusDays(Long.valueOf(days.replace("d", "")));
            }

            String hours = matcher.group(2);
            if (StringUtils.isNotBlank(hours)) {
                duration = duration.plusHours(Long.valueOf(hours.replace("h", "")));
            }

            String minutes = matcher.group(3);
            if (StringUtils.isNotBlank(minutes)) {
                duration = duration.plusMinutes(Long.valueOf(minutes.replace("m", "")));
            }

            String seconds = matcher.group(4);
            if (StringUtils.isNotBlank(seconds)) {
                duration = duration.plusSeconds(Long.valueOf(seconds.replace("s", "")));
            }

            return duration;
        }

        return null;
    }
}
