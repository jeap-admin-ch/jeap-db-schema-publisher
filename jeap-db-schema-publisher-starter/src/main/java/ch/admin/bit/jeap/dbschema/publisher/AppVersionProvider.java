package ch.admin.bit.jeap.dbschema.publisher;

import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;

class AppVersionProvider {

    private final BuildProperties buildProperties;
    private final GitProperties gitProperties;

    AppVersionProvider(BuildProperties buildProperties, GitProperties gitProperties) {
        this.buildProperties = buildProperties;
        this.gitProperties = gitProperties;
    }

    String getVersion() {
        if (buildProperties != null) {
            return buildProperties.getVersion();
        }
        if (gitProperties != null) {
            String gitBuildVersion = gitProperties.get("git.build.version");
            if (gitBuildVersion != null) {
                return gitBuildVersion;
            }
        }
        return "na";
    }
}

