package net.thucydides.core.reports.html;

import com.google.inject.Inject;
import net.thucydides.core.guice.Injectors;
import net.thucydides.core.issues.IssueTracking;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Format text for HTML reports.
 * In particular, this integrates JIRA links into the generated reports.
 */
public class Formatter {

    private static final String ISSUE_NUMBER_REGEXP = "#([A-Z][A-Z0-9-_]*)?-?\\d+";
    private final static Pattern issueNumberPattern = Pattern.compile(ISSUE_NUMBER_REGEXP);
    private final String issueLinkFormat = "<a href=\"{0}\">{1}</a>";

    private final IssueTracking issueTracking;

    public Formatter() {
        this(Injectors.getInjector().getInstance(IssueTracking.class));
    }

    @Inject
    public Formatter(IssueTracking issueTracking) {
        this.issueTracking = issueTracking;
    }


    public static List<String> issuesIn(final String value) {
        Matcher matcher = issueNumberPattern.matcher(value);

        List<String> issues = new ArrayList<String>();
		while (matcher.find()) {
			issues.add(matcher.group());
		}

        return issues;
    }

    public String addLinks(final String value) {
        String formattedValue = value;
        String issueUrlFormat = issueTracking.getIssueTrackerUrl();
        if (issueUrlFormat != null) {
            formattedValue = insertIssueTrackingUrls(value);
        }
        return formattedValue;
    }

    private String insertIssueTrackingUrls(String value) {
        String formattedValue = value;
        String issueUrlFormat = issueTracking.getIssueTrackerUrl();
        List<String> issues = issuesIn(value);
        for(String issue : issues) {
            String issueUrl = MessageFormat.format(issueUrlFormat, stripLeadingHashFrom(issue));
            String issueLink = MessageFormat.format(issueLinkFormat, issueUrl, issue);
            formattedValue = formattedValue.replaceAll(issue, issueLink);
        }
        return formattedValue;
    }

    private String stripLeadingHashFrom(final String issue) {
        return issue.substring(1);
    }
}
