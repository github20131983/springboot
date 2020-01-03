package com.springboot.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.springboot.Application;

@Service
public class FileSystemStorageService implements StorageService{
	
	private final Path rootLocation;
	
	private static final Logger log = LoggerFactory.getLogger(FileSystemStorageService.class);
	
	@Autowired
	public FileSystemStorageService(StorageProperties properties) {
		this.rootLocation = Paths.get(properties.getLocation());
	}
	
	@Override
	public void init() {//创建文件夹
		try {
			Files.createDirectories(rootLocation);//连续创建一系列文件夹
		}
		catch (IOException e) {
			throw new StorageException("Could not initialize storage", e);
		}
	}

	@Override
	public void store(MultipartFile file) {//存储文件
		String filename = StringUtils.cleanPath(file.getOriginalFilename());//文件名
		log.info(filename);
		try {
			if (file.isEmpty()) {
				throw new StorageException("Failed to store empty file " + filename);
			}
			if (filename.contains("..")) {
				throw new StorageException(
						"Cannot store file with relative path outside current directory "
								+ filename);
			}
			try (InputStream inputStream = file.getInputStream()) {
				Files.copy(inputStream, this.rootLocation.resolve(filename),
					StandardCopyOption.REPLACE_EXISTING);
				log.info(this.rootLocation.resolve(filename).toString());
			}
		}
		catch (IOException e) {
			throw new StorageException("Failed to store file " + filename, e);
		}
	}

	@Override
	public Stream<Path> loadAll() {//获取所有的文件名
		try {
			return Files.walk(this.rootLocation, 1)
				.filter(path -> !path.equals(this.rootLocation))
				.map(this.rootLocation::relativize);
		}
		catch (IOException e) {
			throw new StorageException("Failed to read stored files", e);
		}
	}

	@Override
	public Path load(String filename) {//获取全文件路径
		return rootLocation.resolve(filename);
	}

	@Override
	public Resource loadAsResource(String filename) {//将文件名转为资源名，
		try {
			Path file = load(filename);
			Resource resource = new UrlResource(file.toUri());
			log.info(resource.toString());
			if (resource.exists() || resource.isReadable()) {
				return resource;
			}
			else {
				throw new StorageFileNotFoundException(
						"Could not read file: " + filename);
			}
		}
		catch (MalformedURLException e) {
			throw new StorageFileNotFoundException("Could not read file: " + filename, e);
		}
	}

	@Override
	public void deleteAll() {//删除该路径下的所有文件
		FileSystemUtils.deleteRecursively(rootLocation.toFile());
	}

}
