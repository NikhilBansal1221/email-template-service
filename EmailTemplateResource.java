/**
 * @tagline
 * Java-based email personalization microservice for CRM systems, enabling template rendering with dynamic data and conditional logic.
 *
 * @intuition
 * Many CRM campaigns need to send personalized emails using a mix of static customer info and behavioral data. Templates often include logic (e.g., "if user is premium") and require fallback/default behavior.
 *
 * @approach
 * Use Handlebars.java to support merge fields and conditional logic. Expose a REST API using JAX-RS for POST requests, sanitize inputs, validate templates, and render final HTML or plain text output.
 *
 * @complexity
 * Time: O(n) for parsing + rendering where n = size of template and data map
 * Space: O(n) for holding the rendered result
 */

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response.Status;
import java.util.*;
import java.util.logging.*;
import com.github.jknack.handlebars.*;
import com.github.jknack.handlebars.io.*;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

@Path("/email")
@ApplicationScoped
public class EmailTemplateResource {

    private static final Logger LOGGER = Logger.getLogger(EmailTemplateResource.class.getName());
    private static final Handlebars handlebars = new Handlebars();
    private static final PolicyFactory SANITIZER = new HtmlPolicyBuilder().allowStandardUrlProtocols()
            .allowElements("a", "b", "i", "u", "p", "br", "strong", "em", "ul", "ol", "li", "span", "div")
            .allowAttributes("href", "class", "style").onElements("a", "span", "div")
            .toFactory();

    public record EmailRequest(String template, Map<String, Object> data) { }

    public record EmailResponse(String renderedHtml, String renderedText) { }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response renderEmail(EmailRequest request) {
        try {
            Template tmpl = handlebars.compileInline(request.template());

            // Sanitize all string inputs in data
            Map<String, Object> sanitizedData = new HashMap<>();
            for (var entry : request.data().entrySet()) {
                sanitizedData.put(entry.getKey(),
                        entry.getValue() instanceof String
                                ? SANITIZER.sanitize((String) entry.getValue())
                                : entry.getValue());
            }

            String html = tmpl.apply(sanitizedData);
            String text = html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();

            return Response.ok(new EmailResponse(html, text)).build();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Template rendering failed", e);
            return Response.status(Status.BAD_REQUEST)
                    .entity("Template rendering failed: " + e.getMessage())
                    .build();
        }
    }

    public static void main(String[] args) throws Exception {
        EmailTemplateResource resource = new EmailTemplateResource();
        String template = "Hi {{name}}, {{#if premium}}Thanks for being a premium user!{{else}}Check out our premium plan.{{/if}}";
        Map<String, Object> data = Map.of("name", "Alice", "premium", true);
        Response response = resource.renderEmail(new EmailRequest(template, data));
        System.out.println(response.getEntity());
    }
}
