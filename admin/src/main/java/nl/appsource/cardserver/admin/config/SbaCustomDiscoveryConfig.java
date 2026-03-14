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

//            @Override
//            protected URI getServiceUrl(final ServiceInstance instance) {
//                log.info("getServiceUrl() instance.getMetadata(): " + instance.getMetadata());
//                String serviceId = instance.getServiceId();
//
//                // Option A: If routing via an external Ingress/Gateway
//                // return String.format("https://%s.your-ingress-domain.com", serviceId);
//
//                // Option B: If routing via internal Kubernetes DNS (and your SBA server has access to the K8s DNS resolution)
//                String namespace = instance.getMetadata().getOrDefault("k8s_namespace", "default");
//                int port = instance.getPort();
//                final URI result = URI.create(String.format("http://%s.%s.svc.cluster.local:%d", serviceId, namespace, port));
//                log.info("getServiceUrl() result: " + result);
//                return result;
//            }

            @Override
            protected URI getManagementUrl(final ServiceInstance instance) {
//                log.info("getManagementUrl() instance.getMetadata(): " + instance.getMetadata());
//                // Fetch the context path from metadata, defaulting to standard actuator path
//                String contextPath = instance.getMetadata().getOrDefault("management.context-path", "/actuator");
//                final URI result = URI.create(getServiceUrl(instance) + contextPath);
//                log.info("getManagementUrl() result: " + result);
//                return result;

//                log.info("management.port: " + instance.getMetadata().get("management.port"));
                log.info("1 {} {} management.port: {}", instance.getServiceId(), instance.getInstanceId(), instance.getMetadata().get("management.port"));
                return super.getManagementUrl(instance);
            }

            @Override
            protected URI getHealthUrl(final ServiceInstance instance) {
                log.info("2 {} {} management.port: {}", instance.getServiceId(), instance.getInstanceId(), instance.getMetadata().get("management.port"));
//                log.info("getHealthUrl() instance.getMetadata(): " + instance.getMetadata());
//                // Ensure the health endpoint maps correctly
//                String healthPath = instance.getMetadata().getOrDefault("health.path", "/health");
//                final URI result = URI.create(getManagementUrl(instance) + healthPath);
//                log.info("getHealthUrl() result: " + result);
//                return result;
                return super.getHealthUrl(instance);
            }
        };
    }
}
