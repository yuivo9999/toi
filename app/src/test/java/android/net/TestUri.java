package android.net;

import java.util.Collections;
import java.util.List;

public class TestUri extends Uri {
    private final String uriString;

    public TestUri(String uriString) {
        super();
        this.uriString = uriString;
    }

    @Override
    public boolean isHierarchical() {
        return true;
    }

    @Override
    public boolean isRelative() {
        return false;
    }

    @Override
    public String getScheme() {
        return "content";
    }

    @Override
    public String getSchemeSpecificPart() {
        return uriString;
    }

    @Override
    public String getEncodedSchemeSpecificPart() {
        return uriString;
    }

    @Override
    public String getAuthority() {
        return "media";
    }

    @Override
    public String getEncodedAuthority() {
        return "media";
    }

    @Override
    public String getUserInfo() {
        return null;
    }

    @Override
    public String getEncodedUserInfo() {
        return null;
    }

    @Override
    public String getHost() {
        return null;
    }

    @Override
    public int getPort() {
        return -1;
    }

    @Override
    public String getPath() {
        return "/test";
    }

    @Override
    public String getEncodedPath() {
        return "/test";
    }

    @Override
    public String getQuery() {
        return null;
    }

    @Override
    public String getEncodedQuery() {
        return null;
    }

    @Override
    public String getFragment() {
        return null;
    }

    @Override
    public String getEncodedFragment() {
        return null;
    }

    @Override
    public List<String> getPathSegments() {
        return Collections.emptyList();
    }

    @Override
    public String getLastPathSegment() {
        return "test";
    }

    @Override
    public Builder buildUpon() {
        return null;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(android.os.Parcel dest, int flags) {
    }

    @Override
    public String toString() {
        return uriString;
    }
}
