// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.converter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.time.Duration;

public class DurationToStringSerializer extends StdSerializer<Duration> {

    private static final long serialVersionUID = 5848583700556532429L;

    protected DurationToStringSerializer() {
        super(Duration.class);
    }

    @Override
    public void serialize(Duration duration, JsonGenerator jsonGenerator, SerializerProvider provider)
            throws IOException {
        jsonGenerator.writeString(convert(duration));
    }

    public static String convert(Duration duration) {
        if (duration == null) {
            return null;
        }

        StringBuilder s = new StringBuilder();

        if (duration.toDaysPart() > 0) {
            s.append(duration.toDaysPart()).append("d");
        }

        if (duration.toHoursPart() > 0) {
            s.append(duration.toHoursPart()).append("h");
        }

        if (duration.toMinutesPart() > 0) {
            s.append(duration.toMinutesPart()).append("m");
        }

        if (duration.toSecondsPart() > 0 || s.length() == 0) {
            s.append(duration.toSecondsPart()).append("s");
        }

        return s.toString();
    }
}
