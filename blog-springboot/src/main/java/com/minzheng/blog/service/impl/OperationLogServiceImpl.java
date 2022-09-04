package com.minzheng.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.minzheng.blog.dao.OperationLogDao;
import com.minzheng.blog.dto.OperationLogDTO;
import com.minzheng.blog.entity.OperationLog;
import com.minzheng.blog.service.OperationLogService;
import com.minzheng.blog.util.BeanCopyUtils;
import com.minzheng.blog.util.PageUtils;
import com.minzheng.blog.vo.ConditionVO;
import com.minzheng.blog.vo.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 操作日志服务
 *
 */
@Service
public class OperationLogServiceImpl extends ServiceImpl<OperationLogDao, OperationLog> implements OperationLogService  {
    @Autowired
    private OperationLogDao operationLogDao;
    /**
     * 查询日志列表
     *
     * @param conditionVO 条件
     * @return 日志列表
     */
    @Override
    public PageResult<OperationLogDTO> listOperationLogs(ConditionVO conditionVO) {
        Page<OperationLog> page = new Page<>(PageUtils.getLimitCurrent(), PageUtils.getSize());
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        //根据模块或者描述搜索记录
        wrapper.like(StringUtils.isNotBlank(conditionVO.getKeywords()),OperationLog::getOptModule,conditionVO.getKeywords())
            .or()
            .like(StringUtils.isNotBlank(conditionVO.getKeywords()),OperationLog::getOptDesc,conditionVO.getKeywords())
            .orderByDesc(OperationLog::getId);
        Page<OperationLog> operationLogPage = operationLogDao.selectPage(page, wrapper);
        List<OperationLog> operationLogList = operationLogPage.getRecords();
        List<OperationLogDTO> operationLogDTOList = BeanCopyUtils.copyList(operationLogList, OperationLogDTO.class);
        return new PageResult<>(operationLogDTOList,(int)operationLogPage.getTotal());
    }
}
