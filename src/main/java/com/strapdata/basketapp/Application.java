package com.strapdata.basketapp;

import io.micronaut.runtime.Micronaut;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;

@OpenAPIDefinition(
        info = @Info(
                title = "BasketApp",
                version = "0.2",
                description = "BasketApp REST API",
                license = @License(name = "Apache license", url = "https://github.com/strapdata/basketapp/LICENSE.txt")
        ),
        servers = @Server(
                url = "http://basketapp.941a7aa2-kube1-azure-northeurope.azure.strapcloud.com/swagger/basketapp-0.2.yml"
        ),
        tags = {
                @Tag(name = "basket")
        }
)
public class Application {

    public static void main(String[] args) {
        Micronaut.run(Application.class);
    }
}