package com.lucianafreiberg.pocupload.configuration;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;
import software.amazon.awssdk.regions.Region;

@ConfigurationProperties(prefix = "aws.s3")
@Data
public class S3ClientConfigurationProperties {

    private Region region = Region.US_EAST_1;
    private URI endpoint = null;

    private String accessKeyId;
    private String secretAccessKey;

    private String bucket;

    private int multipartMinPartSize = 5 * 1024 * 1024;

}
