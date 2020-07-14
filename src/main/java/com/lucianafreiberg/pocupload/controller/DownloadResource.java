package com.lucianafreiberg.pocupload.controller;

import com.lucianafreiberg.pocupload.configuration.S3ClientConfigurationProperties;
import com.lucianafreiberg.pocupload.domain.exception.DownloadFailedException;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.SdkResponse;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.core.internal.async.ByteArrayAsyncResponseTransformer;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.Map;
import java.util.Map.Entry;
import java.io.InputStream;

@RestController
@RequestMapping("/inbox")
@Slf4j
public class DownloadResource {

    private final S3AsyncClient s3client;
    private final S3ClientConfigurationProperties s3config;

    public DownloadResource(S3AsyncClient s3client, S3ClientConfigurationProperties s3config) {
        this.s3client = s3client;
        this.s3config = s3config;
    }

    @GetMapping(path = "/{fileKey}")
    public Mono<ResponseEntity<Flux<ByteBuffer>>> downloadFile(@PathVariable("fileKey") String fileKey) {

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(s3config.getBucket())
                .key(fileKey)
                .build();

        return Mono.fromFuture(s3client.getObject(request, new FluxResponseProvider()))
                .map( (response) -> {
                    checkResult(response.sdkResponse);
                    String filename = getMetadataItem(response.sdkResponse, "filename", fileKey);
                    log.info("[I65] filename={}, length={}",filename, response.sdkResponse.contentLength() );

                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_TYPE, response.sdkResponse.contentType())
                            .header(HttpHeaders.CONTENT_LENGTH, Long.toString(response.sdkResponse.contentLength()))
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                            .body(response.flux);
                });
    }
    private String getMetadataItem(GetObjectResponse sdkResponse, String key, String defaultValue) {
        for( Entry<String, String> entry : sdkResponse.metadata().entrySet()) {
            if ( entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return defaultValue;
    }
    private static void checkResult(GetObjectResponse response) {
        SdkHttpResponse sdkResponse = response.sdkHttpResponse();
        if ( sdkResponse != null && sdkResponse.isSuccessful()) {
            return;
        }

        throw new DownloadFailedException(response);
    }


    static class FluxResponseProvider implements AsyncResponseTransformer<GetObjectResponse,FluxResponse> {

        private FluxResponse response;

        @Override
        public CompletableFuture<FluxResponse> prepare() {
            response = new FluxResponse();
            return response.cf;
        }

        @Override
        public void onResponse(GetObjectResponse sdkResponse) {
            this.response.sdkResponse = sdkResponse;
        }

        @Override
        public void onStream(SdkPublisher<ByteBuffer> publisher) {
            response.flux = Flux.from(publisher);
            response.cf.complete(response);
        }

        @Override
        public void exceptionOccurred(Throwable error) {
            response.cf.completeExceptionally(error);
        }

    }
    static class FluxResponse {

        final CompletableFuture<FluxResponse> cf = new CompletableFuture<>();
        GetObjectResponse sdkResponse;
        Flux<ByteBuffer> flux;
    }

}
