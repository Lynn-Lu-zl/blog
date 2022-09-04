package com.minzheng.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.minzheng.blog.dao.PhotoDao;
import com.minzheng.blog.dto.PhotoBackDTO;
import com.minzheng.blog.dto.PhotoDTO;
import com.minzheng.blog.entity.Photo;
import com.minzheng.blog.entity.PhotoAlbum;
import com.minzheng.blog.exception.BizException;
import com.minzheng.blog.service.PhotoAlbumService;
import com.minzheng.blog.service.PhotoService;
import com.minzheng.blog.util.BeanCopyUtils;
import com.minzheng.blog.util.PageUtils;
import com.minzheng.blog.vo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.minzheng.blog.constant.CommonConst.FALSE;
import static com.minzheng.blog.enums.PhotoAlbumStatusEnum.PUBLIC;

/**
 * 照片服务
 *一个相册有很多张图片
 */
@Service
public class PhotoServiceImpl extends ServiceImpl<PhotoDao, Photo> implements PhotoService {
    @Autowired
    private PhotoDao photoDao;
    @Autowired
    private PhotoAlbumService photoAlbumService;

    /**
     * 根据相册id获取照片列表
     *
     * @param condition 条件
     * @return {@link PageResult<PhotoBackDTO>} 照片列表
     */
    @Override
    public PageResult<PhotoBackDTO> listPhotos(ConditionVO condition) {
        LambdaQueryWrapper<Photo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Objects.nonNull(condition.getAlbumId()), Photo::getAlbumId, condition.getAlbumId())
            .eq(Photo::getIsDelete,condition.getIsDelete())
            .orderByDesc(Photo::getId)
            .orderByDesc(Photo::getUpdateTime);
        Page<Photo> page = new Page<>();
        Page<Photo> photoPage = photoDao.selectPage(page, wrapper);
        List<PhotoBackDTO> photoBackDTOList = BeanCopyUtils.copyList(photoPage.getRecords(), PhotoBackDTO.class);
        return new PageResult<>(photoBackDTOList,(int)photoPage.getTotal());
    }

    /**
     * 更新照片信息
     *
     * @param photoInfoVO 照片信息
     */
    @Override
    public void updatePhoto(PhotoInfoVO photoInfoVO) {
        Photo photo = BeanCopyUtils.copyObject(photoInfoVO, Photo.class);
        photoDao.updateById(photo);
    }

    /**
     * 保存照片
     *
     * @param photoVO 照片
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void savePhotos(PhotoVO photoVO) {
        //遍历照片url列表
        List<Photo> photoList = photoVO.getPhotoUrlList().stream().map(item -> Photo.builder()
            .albumId(photoVO.getAlbumId())
            .photoName(IdWorker.getIdStr())
            .photoSrc(item)
            .build()).collect(Collectors.toList());
        this.saveBatch(photoList);
    }

    /**
     * 移动照片相册
     *
     * @param photoVO 照片信息
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updatePhotosAlbum(PhotoVO photoVO) {
        List<Photo> photoList = photoVO.getPhotoIdList().stream().map(item->Photo.builder()
            .id(item)
            .albumId(photoVO.getAlbumId())
            .build())
            .collect(Collectors.toList());
        this.updateBatchById(photoList);
    }

    /**
     * 更新照片删除状态
     *
     * @param deleteVO 删除信息
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updatePhotoDelete(DeleteVO deleteVO) {
        // 更新照片状态
        List<Photo> photoList = deleteVO.getIdList().stream().map(item -> Photo.builder()
            .id(item)
            .isDelete(deleteVO.getIsDelete())
            .build())
            .collect(Collectors.toList());
        this.updateBatchById(photoList);
        // 若恢复照片所在的相册已删除，恢复相册
        if (deleteVO.getIsDelete().equals(FALSE)){
            LambdaQueryWrapper<Photo> wrapper = new LambdaQueryWrapper<>();
            wrapper.select(Photo::getAlbumId)
                .in(Photo::getId,deleteVO.getIdList())
                .groupBy(Photo::getAlbumId);
            List<Photo> photos = photoDao.selectList(wrapper);
            List<PhotoAlbum> photoAlbums = photos.stream().map(item -> PhotoAlbum.builder()
                .id(item.getAlbumId())
                .isDelete(FALSE)
                .build())
                .collect(Collectors.toList());
            photoAlbumService.updateBatchById(photoAlbums);
        }
    }

    /**
     * 删除照片
     *
     * @param photoIdList 照片id列表
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deletePhotos(List<Integer> photoIdList) {
        photoDao.deleteBatchIds(photoIdList);
    }

    /**
     * 根据相册id查看照片列表
     *
     * @param albumId 相册id
     * @return {@link List<PhotoDTO>} 照片列表
     */
    @Override
    public PhotoDTO listPhotosByAlbumId(Integer albumId) {
        LambdaQueryWrapper<PhotoAlbum> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PhotoAlbum::getId,albumId)
            .eq(PhotoAlbum::getIsDelete,FALSE)
            //公开状态
            .eq(PhotoAlbum::getStatus,PUBLIC.getStatus());
        // 根据相册id查询该相册信息
        PhotoAlbum photoAlbum = photoAlbumService.getOne(wrapper);
        if (photoAlbum == null) {
            throw new BizException("相册不存在");
        }


        Page<Photo> page = new Page<>(PageUtils.getCurrent(), PageUtils.getSize());
        LambdaQueryWrapper<Photo> photoWrapper = new LambdaQueryWrapper<>();
        photoWrapper
            .select(Photo::getPhotoSrc)
            .eq(Photo::getAlbumId, albumId)
            .eq(Photo::getIsDelete, FALSE)
            .orderByDesc(Photo::getId);
        Page<Photo> photoPage = photoDao.selectPage(page, photoWrapper);
        List<String> photoList = photoPage.getRecords()
            .stream()
            //照片地址
            .map(Photo::getPhotoSrc)
            .collect(Collectors.toList());

        //创建photoDTO对象
        PhotoDTO photoDTO = PhotoDTO.builder()
            //相册封面
            .photoAlbumCover(photoAlbum.getAlbumCover())
            //相册名
            .photoAlbumName(photoAlbum.getAlbumName())
            //照片列表
            .photoList(photoList)
            .build();
        return photoDTO;
    }
}
