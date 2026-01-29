# Diff Hunter

A Burpsuite extension for comparing HTTP requests and responses to identify differences between them

## Description

Diff Hunter allows you to mark any HTTP request as a "target" and compare it against other requests in real-time. The extension highlights differences at both line and character level, making it easy to spot variations in requests and responses

## Preview

![](/images/preview.png)

## Features

### View Modes

- **Words Mode**: Displays requests and responses as plain text, ideal for analyzing standard HTTP traffic
- **Hexdump Mode**: Displays content in hexadecimal format, useful for analyzing binary data or non-printable characters

### Color System

The extension uses a unified color system for both text highlighting and row coloring:

| Color | Text Highlighting | Row Highlighting |
|-------|-------------------|------------------|
| **Red** | Deleted content (exists in target but not in selected) | Request differences only |
| **Orange** | Modified content (changed between versions) | Response differences only |
| **Green** | Added content (exists in selected but not in target) | Both request and response differences |

#### Light Theme Colors
| Type | RGB Value |
|------|-----------|
| Deleted/Request | `(215, 90, 90)` |
| Modified/Response | `(230, 170, 90)` |
| Added/Both | `(140, 215, 120)` |

#### Dark Theme Colors
| Type | RGB Value |
|------|-----------|
| Deleted/Request | `(106, 26, 26)` |
| Modified/Response | `(140, 90, 30)` |
| Added/Both | `(85, 115, 35)` |

### Table Row Highlighting

When a target is selected, table rows are automatically colored based on where differences are found:

| Row Color | Meaning |
|-----------|---------|
| **Red** | Only the request differs from the target |
| **Orange** | Only the response differs from the target |
| **Green** | Both request and response differ from the target |
| **No color** | No differences (identical to target) |

This allows you to quickly identify at a glance which requests have variations and where those variations occur.

### Target System

1. Mark any request as a target by checking the **Target** checkbox in the table
2. Select a host and endpoint from the dropdown menus
3. The target request/response will be displayed in the right panels
4. Select any other request in the table to compare it against the target
5. Differences are automatically calculated and highlighted

### Difference Navigation

- **Request** and **Response** panels show a list of all found differences
- **Click on any difference** to automatically scroll to its location in the editor
- **Uncheck the "Show" checkbox** next to any difference to hide it from the highlighted view

### Filter Options

The filter system is accessible via the gear button (⚙) and includes the following options:

#### Search Filters

| Option | Description |
|--------|-------------|
| **Search In Requests** | Include request content in the search |
| **Search In Responses** | Include response content in the search |
| **Case Sensitive** | Match exact letter casing |
| **Regex** | Use regular expressions for advanced pattern matching |
| **Negative Search** | Show requests that do NOT match the filter |

#### Difference Type Filters

These filters control which rows are displayed based on their difference status:

| Option | Description |
|--------|-------------|
| **Request Differences Only** | Show/hide rows where only the request differs |
| **Response Differences Only** | Show/hide rows where only the response differs |
| **Request And Response Differences Only** | Show/hide rows where both request and response differ |
| **No Differences** | Show/hide rows that are identical to the target |

All difference type filters are enabled by default. Unchecking a filter will hide rows of that type from the table.

### Request Limit

Controls the maximum number of requests stored in memory. Older requests are automatically removed when the limit is reached (except for marked targets and the currently selected request)

### Capture Control (ON/OFF)

- **OFF**: The extension does not capture any traffic
- **ON**: The extension captures and logs all HTTP traffic passing through Burpsuite

### Theme Support

Diff Hunter automatically detects and adapts to Burpsuite's theme:
- **Dark Mode**: Uses darker highlight colors for better visibility
- **Light Mode**: Uses lighter highlight colors appropriate for light backgrounds

The extension updates colors in real-time when you switch themes in Burpsuite

## Algorithm

Diff Hunter uses the **Myers diff algorithm** (implemented via [java-diff-utils](https://github.com/java-diff-utils/java-diff-utils)) to calculate differences. The comparison works in two stages:

1. **Line-level comparison**: Identifies which lines have been added, deleted, or modified
2. **Character-level comparison**: For modified lines, performs a detailed character-by-character comparison to highlight exact changes

## Installation

1. Download the latest JAR file from the [Releases](https://github.com/Justice-Reaper/Diff-Hunter/releases) page
2. Open Burpsuite
3. Go to **Extensions** > **Installed**
4. Click **Add**
5. Select the JAR file
6. The extension will appear as a new tab called "Diff Hunter"

## Building from Source

### Requirements

- Java 21 or higher
- Maven 3.6 or higher

### Build

```
mvn clean package
```

The compiled JAR will be located at `target/diff-hunter-1.0.jar`

## Usage Example

1. Enable capture by clicking the **ON** button
2. Browse a web application to capture traffic
3. Find a request you want to use as a baseline and check the **Target** checkbox
4. Select the host and endpoint from the dropdown menus
5. Click on other requests in the table to compare them against your target
6. Rows will be colored based on where differences are found (red=request, orange=response, green=both)
7. Review the highlighted differences in the editor panels
8. Use the difference tables to navigate to specific changes
9. Use the gear button filters to show/hide specific difference types
