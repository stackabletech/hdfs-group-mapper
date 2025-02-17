package tech.stackable.hadoop;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.GroupMappingServiceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StackableGroupMapper implements GroupMappingServiceProvider {

  public static final String OPA_MAPPING_URL_PROP = "hadoop.security.group.mapping.opa.policy.url";
  private static final Logger LOG = LoggerFactory.getLogger(StackableGroupMapper.class);
  private static final HttpClient HTTP_CLIENT =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
  private final ObjectMapper json;
  private final URI opaUri;

  public StackableGroupMapper() {
    // Guaranteed to be only called once (Effective Java: Item 3)
    Configuration configuration = HadoopConfigSingleton.INSTANCE.getConfiguration();

    String opaMappingUrl = configuration.get(OPA_MAPPING_URL_PROP);
    if (opaMappingUrl == null) {
      throw new OpaException.UriMissing(OPA_MAPPING_URL_PROP);
    }

    try {
      this.opaUri = URI.create(opaMappingUrl);
    } catch (Exception e) {
      throw new OpaException.UriInvalid(opaMappingUrl, e);
    }

    LOG.debug("OPA mapping URL: {}", opaMappingUrl);

    this.json =
        new ObjectMapper()
            // https://github.com/stackabletech/trino-opa-authorizer/issues/24
            // OPA server can send other fields, such as `decision_id`` when enabling decision logs
            // We could add all the fields we *currently* know, but it's more future-proof to ignore
            // any unknown fields.
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            // Do not include null values
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
  }

  /**
   * Returns list of groups for a user. Internally Hadoop will pass the short name to this function,
   * but this prevents us from effectively separating users with the same names but with different
   * kerberos principals.
   *
   * @param user get groups for this user
   * @return list of groups for a given user
   */
  @Override
  public List<String> getGroups(String user) {
    LOG.info("Calling StackableGroupMapper.getGroups for user \"{}\"", user);

    OpaGroupsQuery query = new OpaGroupsQuery(new OpaGroupsQuery.OpaGroupsQueryInput(user));

    String body;
    try {
      body = json.writeValueAsString(query);
    } catch (JsonProcessingException e) {
      throw new OpaException.SerializeFailed(e);
    }

    LOG.debug("Request body: {}", body);
    HttpResponse<String> response = null;
    try {
      response =
          HTTP_CLIENT.send(
              HttpRequest.newBuilder(opaUri)
                  .header("Content-Type", "application/json")
                  .POST(HttpRequest.BodyPublishers.ofString(body))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      LOG.debug("OPA response: {}", response.body());
    } catch (Exception e) {
      LOG.error(e.getMessage());
      throw new OpaException.QueryFailed(e);
    }

    switch (Objects.requireNonNull(response).statusCode()) {
      case 200:
        break;
      case 404:
        throw new OpaException.EndPointNotFound(opaUri.toString());
      default:
        throw new OpaException.OpaServerError(query.toString(), response);
    }

    OpaQueryResult result;
    try {
      result = json.readValue(response.body(), OpaQueryResult.class);
    } catch (JsonProcessingException e) {
      throw new OpaException.DeserializeFailed(e);
    }
    List<String> groups = result.result;

    LOG.debug("Groups for \"{}\": {}", user, groups);

    return groups;
  }

  /** Caches groups, no need to do that for this provider */
  @Override
  public void cacheGroupsRefresh() {
    // does nothing in this provider of user to groups mapping
    LOG.debug("ignoring cacheGroupsRefresh: caching should be provided by the policy provider");
  }

  /**
   * Adds groups to cache, no need to do that for this provider
   *
   * @param groups unused
   */
  @Override
  public void cacheGroupsAdd(List<String> groups) {
    // does nothing in this provider of user to groups mapping
    LOG.debug(
        "ignoring cacheGroupsAdd for groups [{}]: caching should be provided by the policy provider",
        groups);
  }

  private static class OpaQueryResult {
    public List<String> result;
  }
}
