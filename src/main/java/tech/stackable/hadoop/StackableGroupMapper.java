package tech.stackable.hadoop;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.GroupMappingServiceProvider;
import org.apache.hadoop.util.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

public class StackableGroupMapper implements GroupMappingServiceProvider {
    private static final String OPA_MAPPING_URL_PROP = "hadoop.security.group.mapping.opa.url";
    private final Logger LOG = LoggerFactory.getLogger(StackableGroupMapper.class);
    private final KubernetesClient client;
    private final Configuration configuration;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public StackableGroupMapper() {
        this.client = new DefaultKubernetesClient();
        this.configuration = new Configuration();
    }

    /**
     * Returns list of groups for a user.
     *
     * @param user get groups for this user
     * @return list of groups for a given user
     */
    @Override
    public List<String> getGroups(String user) throws IOException {
        LOG.info("Calling StackableGroupMapper.getGroups...");

        String opaMappingUrl = configuration.get(OPA_MAPPING_URL_PROP);

        if (opaMappingUrl == null) {
            throw new RuntimeException("Config \"" + OPA_MAPPING_URL_PROP + "\" missing");
        }

        URI opaUri = URI.create(opaMappingUrl);
        HttpResponse<String> response = null;
        try {
            response = httpClient.send(
                    HttpRequest.newBuilder(opaUri).header("Content-Type", "application/json").GET().build(),
                    //.POST(HttpRequest.BodyPublishers.ofByteArray(user.getBytes())).build(),
                    HttpResponse.BodyHandlers.ofString());
            LOG.info("Opa response [{}]", response);
        } catch (InterruptedException e) {
            LOG.error(e.getMessage());
        }

        switch (response.statusCode()) {
            case 200:
                break;
            default:
                throw new IOException(opaUri.toString());
        }
        String responseBody = response.body();
        LOG.info("Response body [{}]", responseBody);

        return Lists.newArrayList("me", "myself", "I");
    }

    /**
     * Caches groups, no need to do that for this provider
     */
    @Override
    public void cacheGroupsRefresh() {
        // does nothing in this provider of user to groups mapping
        LOG.info("cacheGroupsRefresh: caching should be provided by the policy provider");
    }

    /**
     * Adds groups to cache, no need to do that for this provider
     *
     * @param groups unused
     */
    @Override
    public void cacheGroupsAdd(List<String> groups) {
        // does nothing in this provider of user to groups mapping
        LOG.info("cacheGroupsAdd: caching should be provided by the policy provider");
    }
}