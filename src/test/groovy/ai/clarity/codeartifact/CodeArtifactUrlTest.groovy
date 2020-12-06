package ai.clarity.codeartifact

import spock.lang.Specification

class CodeArtifactUrlTest extends Specification {
    def "Of with URL"() {
        when:
        CodeArtifactUrl url = CodeArtifactUrl.of("https://domain-id.d.codeartifact.eu-central-1.amazonaws.com/maven/repository/")

        then:
        url.region == 'eu-central-1'
        url.artifactDomain == 'domain'
        url.artifactOwner == 'id'
        url.path == '/maven/repository/'
    }

    def "Of with URL segments"() {
        given:
        String artifactDomain = 'domain'
        String artifactOwner = 'owner'
        String region = 'eu-west-2'
        String path = '/maven/releases'

        when:
        CodeArtifactUrl url = CodeArtifactUrl.of(artifactDomain, artifactOwner, region, path)

        then:
        url.url.toString() == 'https://domain-owner.d.codeartifact.eu-west-2.amazonaws.com/maven/releases/'
    }
}
