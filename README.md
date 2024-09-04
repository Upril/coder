# Multithreaded Video Encoder in Java

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/java-19+-green.svg)](https://www.oracle.com/java/technologies/javase/jdk19-archive-downloads.html)

## Overview

This project is a **multithreaded video encoder** developed in Java, designed for efficient video encoding by leveraging modern Java concurrency utilities. The encoder aims to provide variable compression rates for more complex video scenes.

## Features

- **Multithreading**: Efficient task management using the Java Executor Framework and Fork/Join.
- **Etc**: (WIP)

## Getting Started

### Prerequisites

- **Java 19+**: Ensure you have JDK 19 or later installed.
- **Maven**: Use Maven to manage dependencies and build the project.

### Installation

1. **Clone the Repository**:
    ```bash
    git clone https://github.com/your-username/your-repo.git
    cd your-repo
    ```

2. **Build the Project**:
    Using Maven:
    ```bash
    mvn clean install
    ```
    Or using Gradle:
    ```bash
    gradle build
    ```

3. **Run the Application**:
    ```bash
    java -jar target/video-encoder.jar input-video.mp4 output-video.mp4
    ```
