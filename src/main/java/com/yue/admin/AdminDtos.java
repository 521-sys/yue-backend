package com.yue.admin;
import com.yue.entity.*;
import java.util.List;
import org.springframework.data.domain.*;
public final class AdminDtos {
 private AdminDtos(){}
 public record PageResult<T>(List<T> items,long total,int page,int pageSize) {
  public static <T> PageResult<T> of(Page<T> p){return new PageResult<>(p.getContent(),p.getTotalElements(),p.getNumber()+1,p.getSize());}
 }
 public static PageRequest page(int page,int size,Sort sort) {if(page<1||size<1||size>100)throw new AdminApiException(400,"分页范围不正确");return PageRequest.of(page-1,size,sort);}
 public record LineInput(String speaker,String cantoneseText,String mandarinText,String jyutping,String audioKey){}
 public record SceneSaveRequest(String code,String title,String place,String emoji,String roleA,String roleB,String category,String difficulty,Integer sortOrder,Long version,List<LineInput> lines){}
 public record SceneDetail(Scene scene,List<SceneLine> lines){}
 public record StatusRequest(String status,Long version){}
 public record AudioItem(String id,String name,String audioKey,String url,long size,String contentType,long referenceCount,boolean readOnly,Long version){}
}
