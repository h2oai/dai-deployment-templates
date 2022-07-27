package ai.h2o.mojos.deploy.local.rest.converters;

import org.springframework.core.convert.converter.Converter;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public class MultipartConverter implements Converter<MultipartFile, Resource> {

  @Override
  public Resource convert(MultipartFile source) {
    return source.getResource();
  }
}
