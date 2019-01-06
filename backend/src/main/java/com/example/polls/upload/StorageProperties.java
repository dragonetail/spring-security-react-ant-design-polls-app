package com.example.polls.upload;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@ConfigurationProperties("storage")
public class StorageProperties {

	/**
	 * Folder location for upload files
	 */
	@Setter
	@Getter
	private String location = "upload-dir";
}