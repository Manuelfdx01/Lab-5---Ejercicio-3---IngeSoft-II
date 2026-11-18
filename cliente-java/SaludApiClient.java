
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class SaludApiClient {

    private static final String URL =
            "https://localhost:8443/api/v1/salud";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public void consultar() {

        HttpRequest req = HttpRequest.newBuilder(
                    URI.create(URL)
                )
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        try {
            // TODO 1: enviar la petición
            HttpResponse<String> resp = http.send(
                    req,
                    HttpResponse.BodyHandlers.ofString()
            );

            // TODO 2: comprobar el código HTTP
            if (resp.statusCode() != 200) {
                throw new IOException(
                        "La API respondió con código "
                        + resp.statusCode()
                );
            }

            // TODO 3: mostrar la respuesta
            System.out.println("Respuesta de la API:");
            System.out.println(resp.body());

        } catch (IOException e) {

            System.err.println(
                    "Error de conexión o validación TLS: "
                    + e.getMessage()
            );

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            System.err.println(
                    "La petición fue interrumpida."
            );
        }
    }

    public static void main(String[] args) {

        SaludApiClient client = new SaludApiClient();

        client.consultar();
    }
}
