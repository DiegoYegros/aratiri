package com.aratiri.aratiri.config;


import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
@Data
public class Properties {

    private final Logger logger = LoggerFactory.getLogger(Properties.class);

    @Value("${lnd.path.macaroon.admin}")
    private String adminMacaroonPath;

    @Value("${lnd.path.tls.cert}")
    private String lndTlsCertPath;

    @Value("${grpc.client.lnd.name}")
    private String grpcClientLndName;
    @Value("${grpc.client.lnd.port}")
    private int grpcClientLndPort;

    private String adminMacaroon;

    private String lndTlsCert;

    @PostConstruct
    public void loadProperties() throws IOException {
        try{
            adminMacaroon = Files.readString(Path.of(adminMacaroonPath));
            lndTlsCert = Files.readString(Path.of(lndTlsCertPath));

        } catch (IOException e){
           logger.error("Couldn't get macaroon from path [{}]. Exception is: [{}]", adminMacaroonPath, e.getMessage());
           throw e;
        }
    }
}