package notification.example.notification_service.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse {
    private String id;
    private String ver;
    private String ts;
    private String message;
    private ApiRespParam params;
    private HttpStatus responseCode;

    public ApiResponse(String success, String templateCreatedSuccessfully, String id) {
    }
}
