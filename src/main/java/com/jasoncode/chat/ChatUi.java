package com.jasoncode.chat;

/**
 * 对话循环依赖的 UI 抽象（依赖倒置）：
 * 生产环境由 ConsoleUi 实现，测试可用简单实现替代。
 */
public interface ChatUi {

    /** 读取一行输入；null 表示退出（EOF 或中断）。 */
    String readLine();

    void showError(String message);

    void showWarning(String message);

    void println(String message);
}
