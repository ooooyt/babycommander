package com.ooooyt.babycommander.ui;

public record GenerateTask(String task, boolean isNewProject, String projectName) {
    public GenerateTask(String task, boolean isNewProject) {
        this(task, isNewProject, null);
    }
}
