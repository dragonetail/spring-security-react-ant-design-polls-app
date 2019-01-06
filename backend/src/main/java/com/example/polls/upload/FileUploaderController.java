package com.example.polls.upload;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.xml.bind.DatatypeConverter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@Controller
public class FileUploaderController {
    private final Path rootLocation;

    @Autowired
    public FileUploaderController(final StorageProperties properties) {
        this.rootLocation = Paths.get(properties.getLocation());
    }

    @PostMapping("/fileUpload")
    @ResponseBody
    public FileUploadResponse upload(@RequestParam(value = "uuid") final String uuid,
            @RequestParam(value = "filename") final String filename, @RequestParam(value = "size") final Long size,
            @RequestParam(value = "md5") final String md5,
            @RequestParam(value = "chunks", required = false, defaultValue = "1") final Integer chunks,
            @RequestParam(value = "autoMerge", required = false, defaultValue = "true") final Boolean autoMerge,
            final HttpServletRequest request) {
        // TODO
        // 貌似Tomcat在处理Multipart的时候，所有的Param（包括以上参数）都是存成了临时文件（跟踪request.getParts可以看到），
        // 因此在大规模上传文件的时候，这些频繁的小文件可能会成为性能瓶颈
        // 可以改造所有的参数为JSON，然后客户端和服务器端分别再自行解包处理，Spring有可能能够自行解包
        System.out.println("Uploading..." + filename);
        if (!(request instanceof MultipartHttpServletRequest)) {
            throw new RuntimeException("系统错误，MultipartHttpServletRequest配置无效。");
        }
        final MultipartHttpServletRequest mpRequest = (MultipartHttpServletRequest) request;
        final FileUploadResponse response = new FileUploadResponse();
        response.setUuid(uuid);
        response.setFilename(filename);
        response.setChunks(chunks);

        try {
            Files.createDirectories(this.rootLocation);

            // 检查目标文件目前的状况
            final Path targetFilePath = this.rootLocation.resolve(uuid + "-" + filename);
            final File targetFile = targetFilePath.toFile();
            if (targetFile.exists()) {
                response.setExisted(true);
                response.setOldSize(targetFile.length());
                response.setOldMd5("MD5-ABCDEF-123456");// 应该通过数据库查询返回
            }

            final Map<String, MultipartFile> fileMap = mpRequest.getFileMap();
            // 判断是否分块
            if (chunks == 1) {
                if (fileMap.size() == 1) {
                    final MultipartFile file = ((MultipartFile) (fileMap.values().toArray()[0]));
                    System.out.println("Uploading..." + filename + " saving...");
                    try (InputStream inputStream = file.getInputStream()) {
                        Files.copy(inputStream, targetFilePath, StandardCopyOption.REPLACE_EXISTING);
                        inputStream.close();
                    }
                    // TODO 更改成自己的Copy方法，Copy的同时，直接MD5计算
                    final String targetMd5 = this.md5(targetFilePath.toString());
                    System.out.println(md5 + " \n" + targetMd5);

                    response.setCompleted(true);
                }
                // 若没有文件分块上传，则直接返回当前目标文件情况
            } else {
                // 创建临时文件存放目录
                final Path chunksTempPath = this.rootLocation.resolve(uuid);
                final Resource resource = new UrlResource(chunksTempPath.toUri());
                if (!resource.exists()) {
                    Files.createDirectories(chunksTempPath);
                }

                // 存储上传的块
                for (final String chunk : fileMap.keySet()) {// should like "0" ~ "888"
                    final MultipartFile multipartFile = fileMap.get(chunk);
                    System.out.println(
                            "Uploading..." + filename + " saving[" + chunk + "][" + multipartFile.getName() + "]...");
                    try (InputStream inputStream = multipartFile.getInputStream()) {
                        Files.copy(inputStream, chunksTempPath.resolve(chunk), StandardCopyOption.REPLACE_EXISTING);
                        inputStream.close();
                    }
                    // TODO 更改成自己的Copy方法，Copy的同时，直接MD5计算
                    final String targetMd5 = this.md5(chunksTempPath.resolve(chunk).toString());
                    System.out.println(multipartFile.getOriginalFilename() + " \n" + targetMd5);
                }

                // 构造返回数据的结构
                final Set<Integer> uploadedChunkSet = new HashSet<Integer>();
                Files.list(chunksTempPath).forEach(chunkPath -> {
                    final long length = chunkPath.toFile().length();
                    final int chunkNo = Integer.valueOf(chunkPath.getFileName().toString());

                    final UploadedChunk uploadedChunk = new UploadedChunk();
                    uploadedChunk.setChunk(chunkNo);
                    uploadedChunk.setSize(length);
                    response.getUploadedChunks().add(uploadedChunk);

                    uploadedChunkSet.add(chunkNo);
                });

                // 判断是否上传完成（仅仅判断上传块的个数）
                response.setCompleted(true);
                for (int i = 0; i < chunks; i++) {
                    if (!uploadedChunkSet.contains(i)) {
                        response.setCompleted(false);
                        break;
                    }
                }

                // 合并文件
                if (response.getCompleted() && autoMerge) {
                    System.out.println("Uploading..." + filename + " merging...");
                    // Files.deleteIfExists(targetFilePath);
                    final OutputStream fileOutputStream = new BufferedOutputStream(
                            Files.newOutputStream(targetFilePath, StandardOpenOption.CREATE));
                    final byte[] buf = new byte[8192];
                    final MessageDigest md = MessageDigest.getInstance("MD5");
                    try {
                        for (long i = 0; i < chunks; i++) {
                            final Path chunkFilePath = chunksTempPath.resolve(String.valueOf(i));
                            final InputStream inputStream = new BufferedInputStream(
                                    Files.newInputStream(chunkFilePath, StandardOpenOption.READ));
                            int len = 0;
                            while ((len = inputStream.read(buf)) != -1) {
                                fileOutputStream.write(buf, 0, len);
                                md.update(buf, 0, len);
                            }
                            inputStream.close();
                        }
                        response.setMerged(true);

                        final byte[] digest = md.digest();
                        final String targetMd5 = DatatypeConverter.printHexBinary(digest).toUpperCase();

                        System.out.println(md5 + " \n" + targetMd5);

                        response.setSize(targetFilePath.toFile().length());
                        response.setMd5(targetMd5);// 应该计算MD5并更新到数据库
                        // TODO:添加到临时任务中，稍后删除临时目录
                    } finally {
                        fileOutputStream.close();
                    }
                }
            }
            System.out.println("Uploaded..." + filename);
            return response;
        } catch (final IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (final NoSuchAlgorithmException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private String md5(final String filename) throws NoSuchAlgorithmException, IOException {
        final MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(Files.readAllBytes(Paths.get(filename)));
        final byte[] digest = md.digest();
        final String myChecksum = DatatypeConverter.printHexBinary(digest).toUpperCase();
        return myChecksum;
    }
}
