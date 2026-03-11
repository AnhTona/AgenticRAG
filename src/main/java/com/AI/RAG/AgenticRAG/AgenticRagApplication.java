package com.AI.RAG.AgenticRAG;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AgenticRagApplication {

	public static void main(String[] args) {
		Runtime rt = Runtime.getRuntime();
		System.out.println(">>> JVM Max Heap: " + (rt.maxMemory() / 1024 / 1024) + " MB");
		System.out.println(">>> JVM Total Heap: " + (rt.totalMemory() / 1024 / 1024) + " MB");
		SpringApplication.run(AgenticRagApplication.class, args);
	}

}
