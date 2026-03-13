package nl.appsource.cardserver.admin.config;

import de.codecentric.boot.admin.server.cloud.discovery.DefaultServiceInstanceConverter;
import de.codecentric.boot.admin.server.cloud.discovery.ServiceInstanceConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

@Slf4j
@Configuration
public class SbaCustomDiscoveryConfig {

    @Bean
    public ServiceInstanceConverter customServiceInstanceConverter() {

        return new DefaultServiceInstanceConverter() {

            @Override
            protected URI getServiceUrl(final ServiceInstance instance) {

                log.info("getServiceUrl() instance.getMetadata(): " + instance.getMetadata());

                String serviceId = instance.getServiceId();

                // Option A: If routing via an external Ingress/Gateway
                // return String.format("https://%s.your-ingress-domain.com", serviceId);

                // Option B: If routing via internal Kubernetes DNS (and your SBA server has access to the K8s DNS resolution)
                String namespace = instance.getMetadata().getOrDefault("k8s_namespace", "default");
                int port = instance.getPort();
                return URI.create(String.format("http://%s.%s.svc.cluster.local:%d", serviceId, namespace, port));
            }

            @Override
            protected URI getManagementUrl(final ServiceInstance instance) {
                log.info("getManagementUrl() instance.getMetadata(): " + instance.getMetadata());
                // Fetch the context path from metadata, defaulting to standard actuator path
                String contextPath = instance.getMetadata().getOrDefault("management.context-path", "/actuator");
                return URI.create(getServiceUrl(instance) + contextPath);
            }

            @Override
            protected URI getHealthUrl(final ServiceInstance instance) {
                log.info("getHealthUrl() instance.getMetadata(): " + instance.getMetadata());
                // Ensure the health endpoint maps correctly
                String healthPath = instance.getMetadata().getOrDefault("health.path", "/health");
                return URI.create(getManagementUrl(instance) + healthPath);
            }
        };
    }
}
