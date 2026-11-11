package com.teacherfeedback.api.client;

public interface OcrClient {
    String recognize(byte[] imageBytes);
}
