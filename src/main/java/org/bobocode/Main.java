package org.bobocode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {
    private static final String NASA_HOST = "api.nasa.gov";
    private static final String BASE_URL = "/mars-photos/api/v1/rovers/curiosity/photos?sol=15&api_key=oxOSwedAqy5AB5pL8uMZyhozlPGx4r27Jc8w0yhL";
    private static final int HTTPS_PORT = 443;
    private static final int HTTP_PORT = 80;

    @SneakyThrows
    public static void main(String[] args) {
        var responseBody = getResponseBody(sendHttpsRequest(formatGetRequest(BASE_URL, NASA_HOST)));
        ObjectMapper mapper = new ObjectMapper();
        var jsonNode = mapper.readValue(responseBody, JsonNode.class);
        var images = jsonNode.findValuesAsText("img_src");
        List<Picture> pictures = new ArrayList<>();
        for (String image : images) {
            URI imgUri = new URI(image);
            var location = getLocation(imgUri.getHost(), formatGetRequest(imgUri.getPath(), imgUri.getHost()));
            URI localUri = new URI(location.get());
            var imgSize = getSize(localUri.getHost(), formatGetRequest(localUri.getPath(), imgUri.getHost()));
            pictures.add(new Picture(image, imgSize));
        }
        var largerPicture = getLargerPicture(pictures);
        System.out.println(largerPicture);
    }

    @SneakyThrows
    private static List<String> sendHttpsRequest(String request) {
        List<String> response;
        try (Socket socket = SSLSocketFactory.getDefault().createSocket(Main.NASA_HOST, HTTPS_PORT)) {
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer.println(request);
            writer.flush();
            response = reader.lines().toList();
        }
        return response;
    }

    @SneakyThrows
    private static Optional<String> getLocation(String host, String request) {
        Optional<String> locationUrl;
        try (Socket socket = new Socket(host, HTTP_PORT)) {
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer.println(request);
            writer.flush();
            Stream<String> response = reader.lines();
            locationUrl = response
                    .filter(header -> header.startsWith("Location:"))
                    .map(location -> location.split("Location: ")[1])
                    .findAny();
        }
        return locationUrl;
    }

    @SneakyThrows
    private static Long getSize(String host, String request) {
        String size;
        try (Socket socket = new Socket(host, HTTP_PORT)) {
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer.println(request);
            writer.flush();
            Stream<String> response = reader.lines();
            size = response
                    .filter(header -> header.startsWith("Content-Length:"))
                    .map(location -> location.split("Content-Length: ")[1])
                    .findAny().get();
        }
        return Long.parseLong(size);
    }

    private static String formatGetRequest(String url, String host) {
        return """
                GET %s HTTP/1.1
                Connection: close
                Host: %s
                """.formatted(url, host);
    }

    private static String getLargerPicture(List<Picture> pictures) {
        return pictures.stream()
                .max(Comparator.comparing(Picture::size))
                .map(Picture::url)
                .orElseThrow();
    }

    private static String getResponseBody(List<String> responseList) {
        int i = 0;
        while (!responseList.get(i).isEmpty()) {
            i++;
        }
        return responseList.get(++i);
    }

    record Picture(String url, Long size) {
    }
}
