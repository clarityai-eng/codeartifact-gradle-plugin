package ai.clarity.codeartifact

class CodeArtifactUrl {
    private final URL url
    private final String artifactDomain
    private final String artifactOwner
    private final String region
    private final String path

    CodeArtifactUrl(URL url) {
        this.url = url
        String[] domainLevels = this.url.getHost().split("\\.")
        path = url.getPath()
        artifactDomain = domainLevels[0].substring(0, domainLevels[0].lastIndexOf("-"))
        artifactOwner = domainLevels[0].substring(domainLevels[0].lastIndexOf("-") + 1)
        region = domainLevels[domainLevels.length - 3]
    }

    CodeArtifactUrl(String artifactDomain, String artifactOwner, String region, String path) {
        this.artifactDomain = artifactDomain
        this.artifactOwner = artifactOwner
        this.region = region
        this.path = normalizePath(path)
        this.url = new URL(String.format("https://%s-%s.d.codeartifact.%s.amazonaws.com/%s", artifactDomain, artifactOwner, region, path))
    }

    static CodeArtifactUrl of(String url) throws MalformedURLException {
        return of(new URL(url))
    }

    static CodeArtifactUrl of(String artifactDomain, String artifactOwner, String region, String path) throws MalformedURLException {
        return new CodeArtifactUrl(artifactDomain, artifactOwner, region, path)
    }

    static CodeArtifactUrl of(URL url) {
        return new CodeArtifactUrl(url)
    }

    private static String normalizePath(String path) {
        if (path.startsWith("/")) {
            path = path.substring(1)
        }
        if (!path.endsWith("/")) {
            path += "/"
        }

        return path
    }

    URL getUrl() {
        return url
    }

    String getArtifactDomain() {
        return artifactDomain
    }

    String getArtifactOwner() {
        return artifactOwner
    }

    String getRegion() {
        return region
    }

    String getPath() {
        return path
    }
}
