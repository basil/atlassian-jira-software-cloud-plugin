package com.atlassian.jira.cloud.jenkins.config;

import com.atlassian.jira.cloud.jenkins.tenantinfo.CloudIdResolver;
import com.atlassian.jira.cloud.jenkins.util.SecretRetriever;
import com.atlassian.jira.cloud.jenkins.util.SiteValidator;
import com.atlassian.jira.cloud.jenkins.util.WebhookUrlValidator;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.inject.Inject;
import java.util.Objects;
import java.util.Optional;

import static com.atlassian.jira.cloud.jenkins.Config.ATLASSIAN_API_URL;
import static java.util.Objects.requireNonNull;

/**
 * This class encapsulates Jira Cloud site configuration to be used to send build information to
 * Jira.
 */
public class JiraCloudSiteConfig2 extends AbstractDescribableImpl<JiraCloudSiteConfig2> {

    public static final String DEFAULT_SITE = "sitename.atlassian.net";

    private final String site;
    private final String webhookUrl;
    private final String credentialsId;

    @DataBoundConstructor
    public JiraCloudSiteConfig2(
            final String site, final String webhookUrl, final String credentialsId) {
        this.site = requireNonNull(site);
        this.webhookUrl = requireNonNull(webhookUrl);
        this.credentialsId = requireNonNull(credentialsId);
    }

    public String getSite() {
        return site;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    /** Auto-generated by IDEA, please regenerate if you change fields of the class */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JiraCloudSiteConfig2 that = (JiraCloudSiteConfig2) o;
        return Objects.equals(site, that.site)
                && Objects.equals(webhookUrl, that.webhookUrl)
                && Objects.equals(credentialsId, that.credentialsId);
    }

    /** Auto-generated by IDEA, please regenerate if you change fields of the class */
    @Override
    public int hashCode() {
        return Objects.hash(site, webhookUrl, credentialsId);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<JiraCloudSiteConfig2> {

        private transient SecretRetriever secretRetriever;
        private transient CloudIdResolver cloudIdResolver;

        @Inject
        public void setSecretRetriever(final SecretRetriever secretRetriever) {
            this.secretRetriever = secretRetriever;
        }

        @Inject
        public void setCloudIdResolver(final CloudIdResolver cloudIdResolver) {
            this.cloudIdResolver = cloudIdResolver;
        }

        public FormValidation doCheckSite(@QueryParameter final String value) {
            if (StringUtils.isEmpty(value)) {
                return FormValidation.error(
                        "Site name can't be empty. Paste your Jira Cloud site name here.");
            }

            if (!SiteValidator.isValid(value)) {
                return FormValidation.error(
                        "Site name is invalid. Paste a valid site name, e.g. sitename.atlassian.net.");
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckWebhookUrl(@QueryParameter final String value) {
            if (StringUtils.isEmpty(value)) {
                return FormValidation.error(
                        "Webhook URL can’t be blank. Paste it from the Jenkins app in Jira.");
            }

            if (!WebhookUrlValidator.isValid(value)) {
                return FormValidation.error("Webhook URL is not a valid URL.");
            }

            if (!WebhookUrlValidator.containsValidQueryParams(value)) {
                return FormValidation.error(
                        "Webhook URL needs to contain query parameter 'jenkins_server_uuid'.");
            }
            return FormValidation.ok();
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillCredentialsIdItems(@QueryParameter final String credentialsId) {
            Jenkins instance = Jenkins.get();
            if (!instance.hasPermission(Jenkins.ADMINISTER)) {
                return new StandardListBoxModel().includeCurrentValue(credentialsId);
            }

            return new StandardListBoxModel()
                    .includeEmptyValue()
                    .includeMatchingAs(
                            ACL.SYSTEM,
                            instance,
                            StringCredentials.class,
                            URIRequirementBuilder.fromUri(ATLASSIAN_API_URL).build(),
                            CredentialsMatchers.always());
        }

        @RequirePOST
        @Restricted(DoNotUse.class) // WebOnly
        @SuppressWarnings("unused")
        public FormValidation doTestConnection(
                @QueryParameter final String site,
                @QueryParameter final String webhookUrl,
                @QueryParameter final String credentialsId) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);

            final Optional<String> maybeCloudId = cloudIdResolver.getCloudId("https://" + site);

            if (!maybeCloudId.isPresent()) {
                return FormValidation.error("Failed to resolve Jira Cloud site: " + site);
            }

            final Optional<String> maybeSecret = secretRetriever.getSecretFor(credentialsId);

            if (!maybeSecret.isPresent()) {
                return FormValidation.error("Failed to retrieve secret");
            }

            // TODO (ARC-1135): call a "ping" endpoint of the Jenkins app to test the connection

            return FormValidation.ok("Successfully validated site credentials");
        }

        @Override
        public String getDisplayName() {
            return "Jira Cloud Site";
        }
    }
}
