package com.williamcallahan.tui4j.compat.bubbles.viewport;

import static java.lang.Math.max;
import static java.lang.Math.min;

import com.williamcallahan.tui4j.ansi.TextWidth;
import com.williamcallahan.tui4j.compat.bubbles.key.Binding;
import com.williamcallahan.tui4j.compat.bubbletea.Command;
import com.williamcallahan.tui4j.compat.bubbletea.KeyPressMessage;
import com.williamcallahan.tui4j.compat.bubbletea.Message;
import com.williamcallahan.tui4j.compat.bubbletea.Model;
import com.williamcallahan.tui4j.compat.bubbletea.UpdateResult;
import com.williamcallahan.tui4j.compat.lipgloss.Style;
import com.williamcallahan.tui4j.compat.x.ansi.Cut;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Scrollable viewport for viewing content larger than the screen.
 * <p>
 * Port of {@code bubbles/viewport/viewport.go}.
 * Handles vertical scrolling and panning of wrapped or raw text content.
 *
 * @see <a href="https://github.com/charmbracelet/bubbles/blob/main/viewport/viewport.go">bubbles/viewport/viewport.go</a>
 */
public class Viewport implements Model {

    private int width;
    private int height;
    private KeyMap keyMap;
    private boolean mouseWheelEnabled;
    private int mouseWheelDelta;
    private int yOffset;
    private int xOffset;
    private int horizontalStep;
    private int yPosition;
    private Style style;
    private boolean highPerformanceRendering;

    private boolean initialized;
    private List<String> lines;
    private int longestLineWidth;

    /**
     * Creates Viewport with default settings.
     */
    public Viewport() {
        this.width = 0;
        this.height = 0;
        this.keyMap = new KeyMap();
        this.mouseWheelEnabled = true;
        this.mouseWheelDelta = 3;
        this.yOffset = 0;
        this.xOffset = 0;
        this.horizontalStep = 0;
        this.yPosition = 0;
        this.style = Style.newStyle();
        this.highPerformanceRendering = false;
        this.initialized = false;
        this.lines = new ArrayList<>();
        this.longestLineWidth = 0;
    }

    /**
     * Creates Viewport with initial content.
     *
     * @param content initial text content
     */
    public Viewport(String content) {
        this();
        setContent(content);
    }

    /**
     * Creates Viewport with content and height.
     *
     * @param content initial text content
     * @param height viewport height
     */
    public Viewport(String content, int height) {
        this(content);
        setHeight(height);
    }

    /**
     * Creates Viewport with content and dimensions.
     *
     * @param content initial text content
     * @param width viewport width
     * @param height viewport height
     */
    public Viewport(String content, int width, int height) {
        this(content, height);
        setWidth(width);
    }

    /**
     * Creates Viewport with content, dimensions, and key bindings.
     *
     * @param content initial text content
     * @param width viewport width
     * @param height viewport height
     * @param keyMap custom key bindings
     */
    public Viewport(String content, int width, int height, KeyMap keyMap) {
        this(content, width, height);
        this.keyMap = keyMap;
    }

    /**
     * Creates a value for this component.
     *
     * @param width width
     * @param height height
     * @return result
     */
    public static Viewport create(int width, int height) {
        Viewport viewport = new Viewport();
        viewport.setWidth(width);
        viewport.setHeight(height);
        return viewport;
    }

    /**
     * Updates the initial values.
     */
    private void setInitialValues() {
        this.keyMap = new KeyMap();
        this.mouseWheelEnabled = true;
        this.mouseWheelDelta = 3;
        this.initialized = true;
    }

    /**
     * Supplies the initial command for the model.
     *
     * @return initial command
     */
    @Override
    public Command init() {
        return null;
    }

    /**
     * Handles at top for this component.
     *
     * @return whether top
     */
    public boolean atTop() {
        return yOffset <= 0;
    }

    /**
     * Handles at bottom for this component.
     *
     * @return whether bottom
     */
    public boolean atBottom() {
        return yOffset >= maxYOffset();
    }

    /**
     * Handles past bottom for this component.
     *
     * @return whether st bottom
     */
    public boolean pastBottom() {
        return yOffset > maxYOffset();
    }

    /**
     * Handles scroll percent for this component.
     *
     * @return result
     */
    public double scrollPercent() {
        if (lines.isEmpty() || height >= lines.size()) {
            return 1.0;
        }
        double y = (double) yOffset;
        double h = (double) height;
        double t = (double) lines.size();
        double v = y / (t - h);
        return max(0.0, min(1.0, v));
    }

    /**
     * Handles horizontal scroll percent for this component.
     *
     * @return result
     */
    public double horizontalScrollPercent() {
        int effectiveWidth = max(0, width - style.getHorizontalFrameSize());
        if (lines.isEmpty() || effectiveWidth >= longestLineWidth) {
            return 1.0;
        }
        double x = (double) xOffset;
        double w = (double) effectiveWidth;
        double t = (double) longestLineWidth;
        double v = x / (t - w);
        return max(0.0, min(1.0, v));
    }

    /**
     * Replaces the viewport content and recalculates dimensions.
     * Resets position if content shrinks above current scroll.
     *
     * @param content new content
     */
    public void setContent(String content) {
        if (content == null || content.isEmpty()) {
            this.lines = new ArrayList<>();
        } else {
            String normalized = content.replace("\r\n", "\n");
            this.lines = new ArrayList<>(Arrays.asList(normalized.split("\n")));
        }
        this.longestLineWidth = findLongestLineWidth(this.lines);
        setXOffset(this.xOffset);

        if (yOffset > lines.size() - 1) {
            gotoBottom();
        }
    }

    /**
     * Handles max yoffset for this component.
     *
     * @return result
     */
    private int maxYOffset() {
        return max(0, lines.size() - height + style.getVerticalFrameSize());
    }

    /**
     * Handles visible lines for this component.
     *
     * @return result
     */
    private List<String> visibleLines() {
        int h = height - style.getVerticalFrameSize();
        int w = width - style.getHorizontalFrameSize();

        if (!lines.isEmpty()) {
            int top = max(0, yOffset);
            int bottom = clamp(yOffset + h, top, lines.size());
            List<String> result = lines.subList(top, bottom);
            if (xOffset == 0 || w <= 0) {
                return new ArrayList<>(result);
            }

            // Cut.cut() provides ANSI-aware horizontal scrolling, preserving all escape
            // sequences while extracting the visible portion [xOffset, xOffset+w).
            // Equivalent to the previous inline cutLine() implementation.
            List<String> cutLines = new ArrayList<>();
            for (String line : result) {
                cutLines.add(Cut.cut(line, xOffset, xOffset + w));
            }
            return cutLines;
        }

        return new ArrayList<>();
    }

    /**
     * Handles page down for this component.
     */
    public void pageDown() {
        if (!atBottom()) {
            scrollDown(height);
        }
    }

    /**
     * Handles page up for this component.
     */
    public void pageUp() {
        if (!atTop()) {
            scrollUp(height);
        }
    }

    /**
     * Handles half page down for this component.
     */
    public void halfPageDown() {
        if (!atBottom()) {
            scrollDown(height / 2);
        }
    }

    /**
     * Handles half page up for this component.
     */
    public void halfPageUp() {
        if (!atTop()) {
            scrollUp(height / 2);
        }
    }

    /**
     * Handles scroll down for this component.
     *
     * @param n n
     */
    public void scrollDown(int n) {
        if (atBottom() || n == 0 || lines.isEmpty()) {
            return;
        }
        setYOffsetInternal(yOffset + n);
    }

    /**
     * Handles scroll up for this component.
     *
     * @param n n
     */
    public void scrollUp(int n) {
        if (atTop() || n == 0 || lines.isEmpty()) {
            return;
        }
        setYOffsetInternal(yOffset - n);
    }

    /**
     * Updates the horizontal step.
     *
     * @param n n
     */
    public void setHorizontalStep(int n) {
        this.horizontalStep = max(n, 0);
    }

    /**
     * Updates the xoffset.
     *
     * @param n n
     */
    public void setXOffset(int n) {
        int effectiveWidth = max(0, width - style.getHorizontalFrameSize());
        this.xOffset = clamp(n, 0, max(0, longestLineWidth - effectiveWidth));
    }

    /**
     * Handles scroll left for this component.
     *
     * @param n n
     */
    public void scrollLeft(int n) {
        setXOffset(xOffset - n);
    }

    /**
     * Handles scroll right for this component.
     *
     * @param n n
     */
    public void scrollRight(int n) {
        setXOffset(xOffset + n);
    }

    /**
     * Handles total line count for this component.
     *
     * @return result
     */
    public int totalLineCount() {
        return lines.size();
    }

    /**
     * Handles visible line count for this component.
     *
     * @return result
     */
    public int visibleLineCount() {
        return visibleLines().size();
    }

    /**
     * Handles goto top for this component.
     */
    public void gotoTop() {
        if (!atTop()) {
            setYOffsetInternal(0);
        }
    }

    /**
     * Handles goto bottom for this component.
     */
    public void gotoBottom() {
        setYOffsetInternal(maxYOffset());
    }

    /**
     * Applies an incoming message and returns the next model state.
     *
     * @param msg msg
     * @return next model state and command
     */
    @Override
    public UpdateResult<Viewport> update(Message msg) {
        if (!initialized) {
            setInitialValues();
        }

        if (msg instanceof KeyPressMessage keyPressMessage) {
            if (Binding.matches(keyPressMessage, keyMap.pageDown())) {
                pageDown();
            } else if (Binding.matches(keyPressMessage, keyMap.pageUp())) {
                pageUp();
            } else if (
                Binding.matches(keyPressMessage, keyMap.halfPageDown())
            ) {
                halfPageDown();
            } else if (Binding.matches(keyPressMessage, keyMap.halfPageUp())) {
                halfPageUp();
            } else if (Binding.matches(keyPressMessage, keyMap.down())) {
                scrollDown(1);
            } else if (Binding.matches(keyPressMessage, keyMap.up())) {
                scrollUp(1);
            } else if (Binding.matches(keyPressMessage, keyMap.left())) {
                scrollLeft(horizontalStep);
            } else if (Binding.matches(keyPressMessage, keyMap.right())) {
                scrollRight(horizontalStep);
            }
        }

        return UpdateResult.from(this);
    }

    /**
     * Renders the model view for display.
     *
     * @return rendered view
     */
    @Override
    public String view() {
        if (highPerformanceRendering) {
            return "\n".repeat(max(0, height - 1));
        }

        int effectiveWidth = max(1, width - style.getHorizontalFrameSize());
        int effectiveHeight = max(1, height - style.getVerticalFrameSize());

        StringBuilder content = new StringBuilder();
        List<String> visible = visibleLines();
        for (String line : visible) {
            content.append(line != null ? line : "").append("\n");
        }

        int linesNeeded = effectiveHeight - visible.size();
        for (int i = 0; i < linesNeeded; i++) {
            content.append("\n");
        }

        String rendered = style
            .width(effectiveWidth)
            .height(effectiveHeight)
            .render(content.toString());
        return rendered;
    }

    /**
     * Handles find longest line width for this component.
     *
     * @param lines lines
     * @return result
     */
    private int findLongestLineWidth(List<String> lines) {
        int w = 0;
        for (String line : lines) {
            int lineWidth = TextWidth.measureCellWidth(
                line != null ? line : ""
            );
            if (lineWidth > w) {
                w = lineWidth;
            }
        }
        return w;
    }

    /**
     * Handles clamp for this component.
     *
     * @param v v
     * @param low low
     * @param high high
     * @return result
     */
    private int clamp(int v, int low, int high) {
        if (high < low) {
            int temp = low;
            low = high;
            high = temp;
        }
        return max(low, min(high, v));
    }

    /**
     * Updates the yoffset internal.
     *
     * @param n n
     */
    private void setYOffsetInternal(int n) {
        this.yOffset = clamp(n, 0, maxYOffset());
    }

    /**
     * Updates the width.
     *
     * @param width width
     */
    public void setWidth(int width) {
        this.width = width;
    }

    /**
     * Updates the height.
     *
     * @param height height
     */
    public void setHeight(int height) {
        this.height = height;
    }

    /**
     * Returns the width.
     *
     * @return result
     */
    public int getWidth() {
        return width;
    }

    /**
     * Returns the height.
     *
     * @return result
     */
    public int getHeight() {
        return height;
    }

    /**
     * Returns the key map.
     *
     * @return result
     */
    public KeyMap getKeyMap() {
        return keyMap;
    }

    /**
     * Reports whether mouse wheel enabled.
     *
     * @return whether mouse wheel enabled
     */
    public boolean isMouseWheelEnabled() {
        return mouseWheelEnabled;
    }

    /**
     * Updates the mouse wheel enabled.
     *
     * @param mouseWheelEnabled mouse wheel enabled
     */
    public void setMouseWheelEnabled(boolean mouseWheelEnabled) {
        this.mouseWheelEnabled = mouseWheelEnabled;
    }

    /**
     * Returns the mouse wheel delta.
     *
     * @return result
     */
    public int getMouseWheelDelta() {
        return mouseWheelDelta;
    }

    /**
     * Updates the mouse wheel delta.
     *
     * @param mouseWheelDelta mouse wheel delta
     */
    public void setMouseWheelDelta(int mouseWheelDelta) {
        this.mouseWheelDelta = mouseWheelDelta;
    }

    /**
     * Returns the yoffset.
     *
     * @return result
     */
    public int getYOffset() {
        return yOffset;
    }

    /**
     * Returns the style.
     *
     * @return result
     */
    public Style getStyle() {
        return style;
    }

    /**
     * Updates the style.
     *
     * @param style style
     */
    public void setStyle(Style style) {
        this.style = style;
    }

    /**
     * Returns whether high performance rendering is enabled.
     *
     * @return true if enabled
     */
    public boolean isHighPerformanceRendering() {
        return highPerformanceRendering;
    }

    /**
     * Enables or disables high performance rendering.
     *
     * @param highPerformanceRendering true to enable
     */
    public void setHighPerformanceRendering(boolean highPerformanceRendering) {
        this.highPerformanceRendering = highPerformanceRendering;
    }

    /**
     * Returns the yposition.
     *
     * @return result
     */
    public int getYPosition() {
        return yPosition;
    }

    /**
     * Updates the yposition.
     *
     * @param yPosition y position
     */
    public void setYPosition(int yPosition) {
        this.yPosition = yPosition;
    }

    /**
     * Returns the xoffset.
     *
     * @return result
     */
    public int getXOffset() {
        return xOffset;
    }

    /**
     * Returns the max yoffset.
     *
     * @return result
     */
    public int getMaxYOffset() {
        return maxYOffset();
    }
}
