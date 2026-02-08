# Diff Hunter

A Burpsuite extension for comparing HTTP requests and responses to identify differences between them

## Description

Diff Hunter allows you to mark any HTTP request as a "target" and compare it against other requests in real-time. The extension highlights differences at both line and character level, making it easy to spot variations in requests and responses

## Preview

![](/images/image_1.png)

![](/images/image_2.png)

## Features

## Algorithm

Diff Hunter uses a **two-stage diff algorithm**:

### Stage 1: Myers Diff Algorithm
Uses the [java-diff-utils](https://github.com/java-diff-utils/java-diff-utils) library to identify line-level changes (added, deleted, or modified lines).

### Stage 2: Ratcliff/Obershelp Similarity                                                                                                                                                          
For lines marked as "modified" by Myers diff, applies the **Ratcliff/Obershelp algorithm** (also known as Gestalt Pattern Matching, the same algorithm used by Python's `difflib.SequenceMatcher`) to determine if lines are truly similar or completely different.                                                                                                                                    
                                                                                                                                                                                                      
Lines are marked as **Modified** (orange) if they meet **either** condition:                                                                                                                        
 - **Similarity threshold**: ≥74% overall similarity (same as Python's difflib.Differ)                                                                                                               
 - **Longest common substring (LCS)**: The longest common substring is ≥50% of the shorter line's length                                                                                                           
                                                                                                                                                                                                      
If neither condition is met, lines are marked as **Deleted + Added** (red + green). This dual approach ensures that lines with significant shared content (like URLs with different query parameters) are correctly identified as modifications rather than completely different lines

### Theme Support

Diff Hunter automatically detects and adapts to Burpsuite's theme:
- **Dark Mode**: Uses darker highlight colors for better visibility
- **Light Mode**: Uses lighter highlight colors appropriate for light backgrounds

### View Modes

Accessible via the **Mode ▼** button:

#### Display Modes
- **Words Mode**: Displays requests and responses as plain text, ideal for analyzing standard HTTP traffic
- **Hexdump Mode**: Displays content in hexadecimal format, useful for analyzing binary data or non-printable characters

#### Diff Modes
- **Line Diff**: Highlights entire lines that differ between target and selected request (faster, overview-focused)
- **Character Diff**: Highlights specific characters that differ within modified lines (detailed, precise)

### Color System

The extension uses a unified color system for both text highlighting and row coloring. The color legend is accessible via the **Colors ▼** button

#### Diff Text Highlighting

When a target is selected, differences between requests are highlighted in different colors to indicate whether they have been added, deleted, or modified.

| Color       | Text Highlighting                                   |
|-------------|-----------------------------------------------------|
| **Red**     | Deleted content (exists in the target but not in the selected version) |
| **Orange**  | Modified content (changed between versions)         |
| **Green**   | Added content (exists in the selected version but not in the target) |

#### Table Row Highlighting

When a target is selected, table rows are automatically colored based on where differences are found

| Row Color | Meaning |
|-----------|---------|
| **Red** | Only the request differs from the target |
| **Orange** | Only the response differs from the target |
| **Green** | Both request and response differ from the target |
| **No color** | No differences (identical to target) |

### Request Limit

Allows the user to set the maximum number of requests stored in memory. Older requests are automatically removed when the limit is reached (except for marked targets and the currently selected request).

### Capture Control (ON/OFF)

- **OFF**: The extension does not capture any traffic
- **ON**: The extension captures and logs all HTTP traffic passing through Burpsuite

### Filter Options

Type in the filter field and press **Enter** to apply the filter. Clearing the field automatically removes the filter. Additional filter options are accessible via the gear button (⚙):

#### Search Filters

| Option | Description |
|--------|-------------|
| **Search In Requests** | Include request content in the search |
| **Search In Responses** | Include response content in the search |
| **Case Sensitive** | Match exact letter casing |
| **Regex** | Use regular expressions for advanced pattern matching |
| **Negative Search** | Show requests that do NOT match the filter |

#### Difference Type Filters

| Option | Description |
|--------|-------------|
| **Request Differences Only** | Show/hide rows where only the request differs |
| **Response Differences Only** | Show/hide rows where only the response differs |
| **Request And Response Differences Only** | Show/hide rows where both request and response differ |
| **No Differences** | Show/hide rows that are identical to the target |

### Target System

1. Mark any request as a target by checking the **Target** checkbox in the table
2. Select a host and endpoint from the dropdown menus
3. The target request/response will be displayed in the right panels
4. Select any other request in the table to compare it against the target
5. Differences are automatically calculated and highlighted

### JTextPanes

The JTextPanes are the panels where differences between the selected request and the target are highlighted in different colors depending on whether the content is added, modified, or deleted. In addition to this, the JTextPanes also include the following features:

#### Context Menu

Right-click inside the JTextPane to access a context menu that allows sending the request or response to:

- Repeater

- Intruder

- Comparer

#### Search Bar

Each editor panel (Selected Request, Selected Response, Target Request, Target Response) includes a **search bar** at the bottom:

- **Text search**: Type any text to find matches in the editor content
- **Navigation**: Use the **←** and **→** buttons to jump between matches
- **Match counter**: Shows current match position and total count (e.g., "2/5")
- **Match highlighting**: Found matches are underlined with a colored line beneath the text

### Difference Navigation

The differences panel uses a **tabbed interface** with two tabs:

- **Requests Tab**: Shows request differences in two side-by-side tables
- **Responses Tab**: Shows response differences in two side-by-side tables
- **Exclusions Tab**: Allows you to define multiple regular expression (regex) patterns used to ignore specific content when calculating differences. These exclusions are specific to the currently selected target and are applied to all incoming requests

#### Requests/Responses Tab

- The **Line** column shows the line number where each difference is located, making it easy to reference specific locations in the original content
- **Click on any difference** to automatically scroll to its location in the editor
- **Uncheck the "Show" checkbox** to hide the difference from the highlighted view
- **Select All / Deselect All buttons**: Each table has its own buttons for independent control
- **Row Copy Shortcut**: You can copy any difference by right-clicking a row and selecting **Copy** from the context menu

#### Exclusions Tab

The **Exclusions** tab in the Differences panel allows you to define patterns that should be ignored when calculating differences:

- **Add exclusion rules**: Define regex patterns for content that should be excluded from diff calculations
- **Scope selection**: Apply exclusions to Selected, Target, or Both sides
- **Type selection**: Apply exclusions to Requests, Responses, or Both
- **Enable/Disable**: Toggle individual rules without deleting them
- **Real-time updates**: Changes are applied immediately to the current comparison
- **Uncheck the "Enabled" checkbox** to disable the regex rule

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

The compiled JAR will be located at `target/diff-hunter-1.0.0.jar`

## Usage Example

1. Enable capture by clicking the **ON** button
2. Browse a web application to capture traffic
3. Find a request you want to use as a baseline and check the **Target** checkbox
4. Select the domain and target from the dropdown menus
5. Click on other requests in the table to compare them against your target
6 Rows are colored based on differences (red = request, orange = response, green = both). Rows identical to the selected target have no color
7. Review the highlighted differences in the editor panels
8. Use the **Request** and **Response** tabs to navigate between difference types
9. Use the **Exclusions** tab to create regex rules that ignore repetitive content across requests, highlighting only the relevant differences
10. Use the difference tables to navigate to specific changes

### Credits

Diff Hunter was inspired by and partially based on the work of [SequenceComparer](https://github.com/GemDem/SequenceComparer). Many thanks to the original author for providing a solid foundation for request and response comparison
