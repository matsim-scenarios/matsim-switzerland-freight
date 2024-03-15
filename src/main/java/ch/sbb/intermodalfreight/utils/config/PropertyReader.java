package ch.sbb.intermodalfreight.utils.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class PropertyReader {
	private static final Logger log = LogManager.getLogger(PropertyReader.class);

    static Properties load(String name) throws IOException {
        log.debug("Reading property file '{}'", name);
        Properties properties = new Properties();
        try (InputStream inputStream = Project.class.getResourceAsStream(name)) {
            properties.load(inputStream);
        }
        return properties;
    }

}
