package com.arteon.controller;

import com.arteon.commons.BaseResponse;
import com.arteon.commons.ErrorCode;
import com.arteon.commons.ResultUtils;
import com.arteon.domain.Team;
import com.arteon.domain.User;
import com.arteon.domain.dto.TeamQuery;
import com.arteon.domain.request.*;
import com.arteon.domain.vo.TeamVO;
import com.arteon.exception.BusinessException;
import com.arteon.service.TeamService;
import com.arteon.service.UserService;
import com.arteon.service.UserTeamService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@CrossOrigin(origins = "{http://localhost:3000}")
@RequestMapping("/team")
@Slf4j
public class TeamController {

    @Resource
    private UserService userService;

    @Resource
    private UserTeamService userTeamService;

    @Resource
    private TeamService teamService;

    /**
     * 添加队伍请求
     */
    @PostMapping("/add")
    public BaseResponse<Long> addTeam(@RequestBody TeamAddRequest teamAddRequest, HttpServletRequest request) {
        // 校验请求参数是否为空
        if (teamAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Team team = new Team();
        BeanUtils.copyProperties(teamAddRequest, team);  // 拷贝公共字段
        // 获取当前用户，可以假定一定会获取到登录用户，因为在该方法中如果获取不到会直接抛异常
        User loginUser = userService.getLoginUser(request);
        long teamId = teamService.addTeam(team, loginUser);
        return ResultUtils.success(teamId);
    }

    /**
     * 更新队伍请求
     */
    @PostMapping("/update")
    public BaseResponse<Boolean> updateTeam(@RequestBody TeamUpdateRequest teamUpdateRequest, HttpServletRequest request) {
        // 判断请求参数是否为空
        if (teamUpdateRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 获取当前用户
        User loginUser = userService.getLoginUser(request);
        boolean result = teamService.updateTeam(teamUpdateRequest, loginUser);
        // 更新失败就直接抛异常
        if (!result) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新失败");
        }
        return ResultUtils.success(true);
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteTeam(@RequestBody TeamDeleteRequest teamDeleteRequest, HttpServletRequest request) {
        // 参数是否为空
        if (teamDeleteRequest == null || teamDeleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        boolean result = teamService.deleteTeam(teamDeleteRequest.getId(), loginUser);
        if (!result) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除失败");
        }
        return ResultUtils.success(true);
    }

    /**
     * 根据id查询队伍
     */
    @GetMapping("/get")
    public BaseResponse<Team> getTeamById(long id) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Team team = teamService.getById(id);
        if (team == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR);
        }
        return ResultUtils.success(team);
    }

    /**
     * 条件查询 Team
     *
     * @param teamQuery 查询条件
     * @param request   HTTP请求
     * @return List Of TeamVO
     */
    @GetMapping("/list")
    public BaseResponse<List<TeamVO>> listTeams(TeamQuery teamQuery, HttpServletRequest request) {
        /*
        先分析到底要返回什么信息？返回 List of TeamVO，里面包含什么信息？
        Team缺少的字段还有三个
        createUser - 队伍创建者
        hasJoinNum - 队伍人数
        hasJoin - 当前用户是否入队
         */
        // 校验参数
        if (teamQuery == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 获取当前用户（假定一定获取到，该方法没有获取到会直接抛异常）
        User loginUser = userService.getLoginUser(request);
        // 调用业务层方法查询 TeamVO 列表
        List<TeamVO> teamVOList = teamService.listTeam(teamQuery, loginUser);
        // 返回
        return ResultUtils.success(teamVOList);
    }

    // todo 查询分页
    @GetMapping("/list/page")
    public BaseResponse<Page<Team>> listTeamsByPage(TeamQuery teamQuery) {
        if (teamQuery == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Team team = new Team();
        BeanUtils.copyProperties(teamQuery, team);
        Page<Team> page = new Page<>(teamQuery.getPageNum(), teamQuery.getPageSize());
        QueryWrapper<Team> queryWrapper = new QueryWrapper<>(team);
        Page<Team> resultPage = teamService.page(page, queryWrapper);
        return ResultUtils.success(resultPage);
    }

    @PostMapping("/join")
    public BaseResponse<Boolean> joinTeam(@RequestBody TeamJoinRequest teamJoinRequest, HttpServletRequest request) {
        if (teamJoinRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        boolean result = teamService.joinTeam(teamJoinRequest, loginUser);
        return ResultUtils.success(result);
    }

    @PostMapping("/quit")
    public BaseResponse<Boolean> quitTeam(@RequestBody TeamQuitRequest teamQuitRequest, HttpServletRequest request) {
        if (teamQuitRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        boolean result = teamService.quitTeam(teamQuitRequest, loginUser);
        return ResultUtils.success(result);
    }

    /**
     * 获取当前用户的队伍（不一定是创建者，因为存在队长转移的情况）
     *
     * @param request HTTP请求
     * @return List of TeamVO
     */
    @GetMapping("/list/my/create")
    public BaseResponse<List<TeamVO>> listMyCreateTeams(HttpServletRequest request) {
        // 获取当前用户、调用业务层方法
        User loginUser = userService.getLoginUser(request);
        List<TeamVO> teamVOList = teamService.myCreateTeamList(loginUser.getId());
        return ResultUtils.success(teamVOList);
    }

    @GetMapping("/list/my/join")
    public BaseResponse<List<TeamVO>> listMyJoinTeams(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        List<TeamVO> teamList = teamService.myJoinTeamList(loginUser);
        return ResultUtils.success(teamList);
    }

}
