package com.minzheng.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.minzheng.blog.dao.PhotoAlbumDao;
import com.minzheng.blog.dao.PhotoDao;
import com.minzheng.blog.dto.PhotoAlbumBackDTO;
import com.minzheng.blog.dto.PhotoAlbumDTO;
import com.minzheng.blog.entity.Photo;
import com.minzheng.blog.entity.PhotoAlbum;
import com.minzheng.blog.exception.BizException;
import com.minzheng.blog.service.PhotoAlbumService;
import com.minzheng.blog.util.BeanCopyUtils;
import com.minzheng.blog.util.PageUtils;
import com.minzheng.blog.vo.ConditionVO;
import com.minzheng.blog.vo.PageResult;
import com.minzheng.blog.vo.PhotoAlbumVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.minzheng.blog.constant.CommonConst.FALSE;
import static com.minzheng.blog.constant.CommonConst.TRUE;
import static com.minzheng.blog.enums.PhotoAlbumStatusEnum.PUBLIC;


/**
 * 相册服务
 * 一个相册有很多张图片
 *
 */
@Service
public class PhotoAlbumServiceImpl extends ServiceImpl<PhotoAlbumDao, PhotoAlbum> implements PhotoAlbumService {
    @Autowired
    private PhotoAlbumDao photoAlbumDao;
    @Autowired
    private PhotoDao photoDao;

    /**
     * 保存或更新相册
     * 查询相册名是否存在--》存在报异常
     *
     * @param photoAlbumVO 相册信息
     */
    @Override
    public void saveOrUpdatePhotoAlbum(PhotoAlbumVO photoAlbumVO) {
        PhotoAlbum photoAlbum = photoAlbumDao.selectOne(new LambdaQueryWrapper<PhotoAlbum>().select(PhotoAlbum::getId).eq(PhotoAlbum::getAlbumName, photoAlbumVO.getAlbumName()));
        if (photoAlbum != null) {
            throw new BizException("相册名已存在");
        }else{
            PhotoAlbum album = BeanCopyUtils.copyObject(photoAlbumVO, PhotoAlbum.class);
            this.saveOrUpdate(album);
        }

    }

    /**
     * 查看后台相册列表
     *
     * @param condition 条件
     * @return {@link PageResult<PhotoAlbumBackDTO>} 相册列表
     */
    @Override
    public PageResult<PhotoAlbumBackDTO> listPhotoAlbumBacks(ConditionVO condition) {
        // 查询相册数量
        Integer count = photoAlbumDao.selectCount(new LambdaQueryWrapper<PhotoAlbum>()
            .like(StringUtils.isNotBlank(condition.getKeywords()), PhotoAlbum::getAlbumName, condition.getKeywords())
            .eq(PhotoAlbum::getIsDelete, FALSE));
        if (count == 0) {
            return new PageResult<>();
        }
        // 查询相册信息
        List<PhotoAlbumBackDTO> photoAlbumBackList = photoAlbumDao.listPhotoAlbumBacks(PageUtils.getLimitCurrent(), PageUtils.getSize(), condition);
        return new PageResult<>(photoAlbumBackList, count);
    }

    /**
     * 获取后台相册列表信息
     *
     * @return {@link List<PhotoAlbumDTO>} 相册列表信息
     */
    @Override
    public List<PhotoAlbumDTO> listPhotoAlbumBackInfos() {
        List<PhotoAlbum> photoAlbumList = photoAlbumDao.selectList(new LambdaQueryWrapper<PhotoAlbum>().eq(PhotoAlbum::getIsDelete, FALSE));
        List<PhotoAlbumDTO> photoAlbumDTOList = BeanCopyUtils.copyList(photoAlbumList, PhotoAlbumDTO.class);
        return photoAlbumDTOList;
    }

    /**
     * 根据id获取相册信息
     *
     * @param albumId 相册id
     * @return {@link PhotoAlbumBackDTO} 相册信息
     */
    @Override
    public PhotoAlbumBackDTO getPhotoAlbumBackById(Integer albumId) {
        // 查询相册信息
        PhotoAlbum photoAlbum = photoAlbumDao.selectById(albumId);
        // 查询照片数量
        Integer photoCount = photoDao.selectCount(new LambdaQueryWrapper<Photo>()
            .eq(Photo::getAlbumId, albumId)
            .eq(Photo::getIsDelete, FALSE));
        PhotoAlbumBackDTO album = BeanCopyUtils.copyObject(photoAlbum, PhotoAlbumBackDTO.class);
        album.setPhotoCount(photoCount);
        return album;
    }

    /**
     * 根据id删除相册
     * 如果相册有图片--》逻辑删除图片
     * 没有图片--》直接删除相册
     *
     * @param albumId 相册id
     */
    @Override
    public void deletePhotoAlbumById(Integer albumId) {
        Integer count = photoDao.selectCount(new LambdaQueryWrapper<Photo>().eq(Photo::getAlbumId, albumId));
        if (count > 0){
            photoAlbumDao.updateById(PhotoAlbum.builder().id(albumId).isDelete(TRUE).build());
            photoDao.update(new Photo(), new LambdaUpdateWrapper<Photo>()
                .set(Photo::getIsDelete, TRUE)
                .eq(Photo::getAlbumId, albumId));
        }else{
            // 若相册下不存在照片则直接删除
            photoAlbumDao.deleteById(albumId);
        }

    }

    /**
     * 获取相册列表
     *
     * @return {@link List<PhotoAlbumDTO>}相册列表
     */
    @Override
    public List<PhotoAlbumDTO> listPhotoAlbums() {
        // 查询相册列表
        List<PhotoAlbum> photoAlbumList = photoAlbumDao.selectList(new LambdaQueryWrapper<PhotoAlbum>()
            .eq(PhotoAlbum::getStatus, PUBLIC.getStatus())
            .eq(PhotoAlbum::getIsDelete, FALSE)
            .orderByDesc(PhotoAlbum::getId));
        return BeanCopyUtils.copyList(photoAlbumList, PhotoAlbumDTO.class);
    }
}
