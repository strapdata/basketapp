package com.strapdata.basketapp.controllers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Expose the Swagger descriptor with an URL targeting the k8s service and the app context.
 */
@Controller("/swagger")
public class SwaggerController {

    private static final Logger logger = LoggerFactory.getLogger(SwaggerController.class);

    final String yamlDescriptor;

    public SwaggerController() throws URISyntaxException, IOException {
        String descriptor = new String(Files.readAllBytes(Paths.get(getClass().getClassLoader().getResource("META-INF/swagger/basketapp-0.2.yml").toURI())));
        Yaml yaml = new Yaml();
        Map<String, Object> yamlMap = yaml.load(descriptor);
        yamlMap.put("servers", ImmutableList.of(ImmutableMap.of("url",
                "http://" + System.getProperty("SERVICE_NAME", "basketapp") + ":" + Integer.getInteger("SERVICE_PORT", 8080) + "/basketapp")));
        yamlDescriptor = yaml.dump(yamlMap);
    }

    @Get(value = "/", produces = MediaType.APPLICATION_YAML)
    public String descriptor() {
        return yamlDescriptor;
    }
}
