package com.arteon.service.impl;

import com.arteon.commons.ErrorCode;
import com.arteon.commons.TeamStatusEnum;
import com.arteon.domain.Team;
import com.arteon.domain.User;
import com.arteon.domain.UserTeam;
import com.arteon.domain.dto.TeamQuery;
import com.arteon.domain.request.TeamJoinRequest;
import com.arteon.domain.request.TeamQuitRequest;
import com.arteon.domain.request.TeamUpdateRequest;
import com.arteon.domain.vo.TeamVO;
import com.arteon.domain.vo.UserVO;
import com.arteon.exception.BusinessException;
import com.arteon.mapper.TeamMapper;
import com.arteon.service.TeamService;
import com.arteon.service.UserService;
import com.arteon.service.UserTeamService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class TeamServiceImpl extends ServiceImpl<TeamMapper, Team> implements TeamService {

    @Resource
    private UserService userService;

    @Resource
    private UserTeamService userTeamService;

    @Resource
    private RedissonClient redissonClient;

    /**
     * 创建队伍
     *
     * @param team      添加的队伍
     * @param loginUser 当前登录用户
     * @return 新队伍的id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    /*
    这个方法涉及多次数据库修改操作，需要使用事务。rollbackFor用于指定出现什么异常时需要回滚？
    这里是出现任何异常都回滚。默认是出现RuntimeException才回滚
     */
    public long addTeam(Team team, User loginUser) {
        // 1. 请求参数是否为空？
        if (team == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 2. 是否登录，未登录不允许创建
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        final long userId = loginUser.getId();
        // 3. 校验信息
        //   1. 队伍人数 > 1 且 <= 20
        int maxNum = Optional.ofNullable(team.getMaxNum()).orElse(0);
        if (maxNum < 1 || maxNum > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍人数不满足要求");
        }
        //   2. 队伍标题 <= 20
        String name = team.getName();
        if (StringUtils.isBlank(name) || name.length() > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍标题不满足要求");
        }
        //   3. 描述 <= 512
        String description = team.getDescription();
        if (StringUtils.isNotBlank(description) && description.length() > 512) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍描述过长");
        }
        //   4. status 是否公开（int）不传默认为 0（公开）
        int status = Optional.ofNullable(team.getStatus()).orElse(0);
        TeamStatusEnum statusEnum = TeamStatusEnum.getEnumByValue(status);
        if (statusEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍状态不满足要求");
        }
        //   5. 如果 status 是加密状态，一定要有密码，且密码 <= 32
        String password = team.getPassword();
        if (TeamStatusEnum.SECRET.equals(statusEnum)) {  // 用equals比较两个枚举
            if (StringUtils.isBlank(password) || password.length() > 32) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码设置不正确");
            }
        }
        // 6. 超时时间 > 当前时间
        Date expireTime = team.getExpireTime();
        if (new Date().after(expireTime)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "超时时间 > 当前时间");
        }
        // 7. 校验用户最多创建 5 个队伍
        // todo 用户如果疯狂点击可能同时创建多个队伍，这里还需完善
        QueryWrapper<Team> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", userId);
        long hasTeamNum = this.count(queryWrapper);
        if (hasTeamNum >= 5) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户最多创建 5 个队伍");
        }
        // 8. 插入队伍信息到队伍表
        team.setId(null);
        team.setUserId(userId);
        boolean result = this.save(team);
        Long teamId = team.getId();
        if (!result || teamId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "创建队伍失败");
        }
        // 9. 插入用户  => 队伍关系到关系表
        UserTeam userTeam = new UserTeam();
        userTeam.setUserId(userId);
        userTeam.setTeamId(teamId);
        userTeam.setJoinTime(new Date());
        result = userTeamService.save(userTeam);
        if (!result) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "创建队伍失败");
        }
        return teamId;
    }

    /**
     * 更新队伍
     */
    @Override
    public boolean updateTeam(TeamUpdateRequest teamUpdateRequest, User loginUser) {
        // 校验请求参数
        Long id = teamUpdateRequest.getId();
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 判断原来的队伍是否存在
        Team oldTeam = this.getById(id);
        if (oldTeam == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "队伍不存在");
        }
        // 如果队伍状态是加密的，还必须要有密码
        Integer status = teamUpdateRequest.getStatus();
        if (TeamStatusEnum.SECRET.equals(TeamStatusEnum.getEnumByValue(status))) {
            if (StringUtils.isBlank(teamUpdateRequest.getPassword())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "加密房间必须有密码");
            }
        }
        // 鉴权，只有队长或者管理员才允许更改
        if (!userService.isAdmin(loginUser) && !loginUser.getId().equals(oldTeam.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }
        // 更新
        Team team = new Team();
        BeanUtils.copyProperties(teamUpdateRequest, team);
        return this.updateById(team);
    }

    /**
     * 根据id删除队伍
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteTeam(long teamId, User loginUser) {
        // 判断队伍是否存在
        Team team = getTeamById(teamId);  // 假定一定会获取到，该方法中如果没有获取到会直接抛异常
        Long userId = team.getUserId();
        // 鉴权，只能队长删除队伍
        if (!userId.equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }
        // 删除，注意涉及多表，需要多次修改数据库
        // 删除user_team表中的数据
        QueryWrapper<UserTeam> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", userId);
        boolean removed = userTeamService.remove(queryWrapper);
        if (!removed) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除队伍关联信息失败");
        }
        // 删除队伍（team表中的数据）
        return this.removeById(teamId);
    }

    /**
     * 查询队伍列表
     *
     * @param teamQuery 封装的请求参数类
     * @param loginUser 当前登录用户
     * @return List of TeamVO
     */
    @Override
    public List<TeamVO> listTeam(TeamQuery teamQuery, User loginUser) {
        // 先根据条件查询 Team（拼接条件，注意过期时间）
        QueryWrapper<Team> teamQueryWrapper = new QueryWrapper<>();
        if (teamQuery.getId() != null && teamQuery.getId() > 0) {
            teamQueryWrapper.eq("id", teamQuery.getId());
        }
        if (teamQuery.getIdList() != null && !teamQuery.getIdList().isEmpty()) {
            teamQueryWrapper.in("id", teamQuery.getIdList());
        }
        if (teamQuery.getSearchText() != null && !teamQuery.getSearchText().isEmpty()) {
            teamQueryWrapper.and(qw -> qw.like("name", teamQuery.getSearchText()).or().like("description", teamQuery.getSearchText()));
        }
        if (teamQuery.getName() != null && !teamQuery.getName().isEmpty()) {
            teamQueryWrapper.like("name", teamQuery.getName());
        }
        if (teamQuery.getDescription() != null && !teamQuery.getDescription().isEmpty()) {
            teamQueryWrapper.like("description", teamQuery.getDescription());
        }
        if (teamQuery.getMaxNum() != null && teamQuery.getMaxNum() > 0) {
            teamQueryWrapper.eq("maxNum", teamQuery.getMaxNum());
        }
        if (teamQuery.getUserId() != null && teamQuery.getUserId() > 0) {
            teamQueryWrapper.eq("userId", teamQuery.getUserId());
        }
        // 还有队伍过期时间
        teamQueryWrapper.and(qw -> qw.gt("expireTime", new Date()).or().isNull("expireTime"));
        // 根据队伍状态查询
        // 需求：如果是管理员可以查询任意的队伍，普通用户不允许查私有的队伍
        TeamStatusEnum statusEnum = TeamStatusEnum.getEnumByValue(teamQuery.getStatus());
        // 进入 if 说明 status不合法，默认查公开队伍
        if (statusEnum == null) {
            statusEnum = TeamStatusEnum.PUBLIC;
        }
        // 鉴权
        if (!userService.isAdmin(loginUser) && statusEnum.equals(TeamStatusEnum.PRIVATE)) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }
        // 查询
        List<Team> teamList = this.list(teamQueryWrapper);
        if (CollectionUtils.isEmpty(teamList)) {
            return new ArrayList<>();  // 什么都没查到，直接返回空的 List
        }
        // 然后转换成 TeamVO，并补充缺少的字段，脱敏
        // Team缺少的字段还有三个
        // createUser - 队伍创建者 √
        // hasJoin - 当前用户是否入队 √
        // hasJoinNum - 队伍人数
        ArrayList<TeamVO> teamVOList = new ArrayList<>();  // 最终要返回的
        for (Team team : teamList) {
            // 获取队伍的创建者
            Long userId = team.getUserId();
            if (userId == null || userId <= 0) {
                continue;
            }
            User user = userService.getById(userId);
            if (user == null) {
                continue;
            }
            UserVO userVO = new UserVO();
            BeanUtils.copyProperties(user, userVO);  // 相当于脱敏了，VO类的本意之一就是去除敏感数据
            // 将 team 转成 teamVO
            TeamVO teamVO = new TeamVO();
            BeanUtils.copyProperties(team, teamVO);
            teamVO.setCreateUser(userVO);
            // 判断当前用户是否已入队
            QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
            userTeamQueryWrapper.eq("userId", loginUser.getId());
            userTeamQueryWrapper.eq("teamId", team.getId());
            long count = userTeamService.count(userTeamQueryWrapper);
            teamVO.setHasJoin(count > 0);
            // 查询队伍的当前人数
            userTeamQueryWrapper = new QueryWrapper<>();
            userTeamQueryWrapper.eq("teamId", team.getId());
            long num = userTeamService.count(userTeamQueryWrapper);
            teamVO.setHasJoinNum((int) num);
            teamVOList.add(teamVO);  // 添加到最后要返回的 List
        }
        return teamVOList;
    }

    /**
     * 获取当前用户创建的队伍
     *
     * @param id 当前用户 id
     * @return List of TeamVO
     */
    @Override
    public List<TeamVO> myCreateTeamList(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        QueryWrapper<Team> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", id);
        // 排除过期时间
        queryWrapper.and(qw -> qw.gt("expireTime", new Date()).or().isNull("expireTime"));
        // 查询
        List<Team> teamList = this.list(queryWrapper);
        if (CollectionUtils.isEmpty(teamList)) {
            return new ArrayList<>();  // 什么都没查到，直接返回空的 List
        }
        // 然后转换成 TeamVO，并补充缺少的字段，脱敏
        // Team缺少的字段还有三个
        // createUser - 队伍创建者 √
        // hasJoin - 当前用户是否入队 √
        // hasJoinNum - 队伍人数
        ArrayList<TeamVO> teamVOList = new ArrayList<>();  // 最终要返回的
        for (Team team : teamList) {
            // 获取队伍的创建者
            Long userId = team.getUserId();
            if (userId == null || userId <= 0) {
                continue;
            }
            User user = userService.getById(userId);
            if (user == null) {
                continue;
            }
            UserVO userVO = new UserVO();
            BeanUtils.copyProperties(user, userVO);
            // 将 team 转成 teamVO
            TeamVO teamVO = new TeamVO();
            BeanUtils.copyProperties(team, teamVO);
            teamVO.setCreateUser(userVO);
            // 判断当前用户是否已入队
            QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
            userTeamQueryWrapper.eq("userId", id);
            userTeamQueryWrapper.eq("teamId", team.getId());
            long count = userTeamService.count(userTeamQueryWrapper);
            teamVO.setHasJoin(count > 0);
            // 查询队伍的当前人数
            userTeamQueryWrapper = new QueryWrapper<>();
            userTeamQueryWrapper.eq("teamId", team.getId());
            long num = userTeamService.count(userTeamQueryWrapper);
            teamVO.setHasJoinNum((int) num);
            teamVOList.add(teamVO);  // 添加到最后要返回的 List
        }
        return teamVOList;
    }

    /**
     * 获取当前用户加入的队伍
     *
     * @param loginUser 当前登录用户
     * @return List of TeamVO
     */
    @Override
    public List<TeamVO> myJoinTeamList(User loginUser) {
        QueryWrapper<UserTeam> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", loginUser.getId());
        List<UserTeam> userTeamList = userTeamService.list(queryWrapper);
        ArrayList<Long> joinedTeamIdList = new ArrayList<>();
        for (UserTeam userTeam : userTeamList) {
            joinedTeamIdList.add(userTeam.getTeamId());
        }
        QueryWrapper<Team> teamQueryWrapper = new QueryWrapper<>();
        if (joinedTeamIdList.isEmpty()) {
            return new ArrayList<>();  // 没加入任何队伍，直接返回空的 List
        }
        teamQueryWrapper.in("id", joinedTeamIdList);
        List<Team> teamList = this.list(teamQueryWrapper);
        if (CollectionUtils.isEmpty(teamList)) {
            return new ArrayList<>();  // 什么都没查到，直接返回空的 List
        }
        // 然后转换成 TeamVO，并补充缺少的字段，脱敏
        // Team缺少的字段还有三个
        // createUser - 队伍创建者 √
        // hasJoin - 当前用户是否入队 √
        // hasJoinNum - 队伍人数
        ArrayList<TeamVO> teamVOList = new ArrayList<>();  // 最终要返回的
        for (Team team : teamList) {
            // 获取队伍的创建者
            Long userId = team.getUserId();
            if (userId == null || userId <= 0) {
                continue;
            }
            User user = userService.getById(userId);
            if (user == null) {
                continue;
            }
            UserVO userVO = new UserVO();
            BeanUtils.copyProperties(user, userVO);
            // 将 team 转成 teamVO
            TeamVO teamVO = new TeamVO();
            BeanUtils.copyProperties(team, teamVO);
            teamVO.setCreateUser(userVO);
            // 判断当前用户是否已入队
            QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
            userTeamQueryWrapper.eq("userId", loginUser.getId());
            userTeamQueryWrapper.eq("teamId", team.getId());
            long count = userTeamService.count(userTeamQueryWrapper);
            teamVO.setHasJoin(count > 0);
            // 查询队伍的当前人数
            userTeamQueryWrapper = new QueryWrapper<>();
            userTeamQueryWrapper.eq("teamId", team.getId());
            long num = userTeamService.count(userTeamQueryWrapper);
            teamVO.setHasJoinNum((int) num);
            teamVOList.add(teamVO);  // 添加到最后要返回的 List
        }
        return teamVOList;
    }

    /**
     * 加入队伍
     *
     * @param teamJoinRequest 封装的请求参数类
     * @param loginUser       当前登录用户
     * @return true表示操作成功，false操作失败
     */
    @Override
    public boolean joinTeam(TeamJoinRequest teamJoinRequest, User loginUser) {
        if (teamJoinRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 队伍不能过期
        Team team = this.getTeamById(teamJoinRequest.getTeamId());
        Date expireTime = team.getExpireTime();  // 过期时间
        if (expireTime.before(new Date())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍过期");
        }
        // 不能加入私人队伍
        TeamStatusEnum statusEnum = TeamStatusEnum.getEnumByValue(team.getStatus());
        if (TeamStatusEnum.PRIVATE.equals(statusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不能加入私有队伍");
        }
        // 如果是加密队伍需要密码正确
        if (TeamStatusEnum.SECRET.equals(statusEnum)) {
            if (!teamJoinRequest.getPassword().equals(team.getPassword())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
            }
        }
        // 获取分布式锁
        RLock lock = redissonClient.getLock("pm:join_team");
        try {
            while (true) {
                if (lock.tryLock(0, -1, TimeUnit.MILLISECONDS)) {
                    // 最多只能加入5个队伍
                    QueryWrapper<UserTeam> queryWrapper = new QueryWrapper<>();
                    queryWrapper.eq("userId", loginUser.getId());
                    long count = userTeamService.count(queryWrapper);
                    if (count >= 5) {
                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "一个用户最多加入或创建5个队伍");
                    }
                    // 队伍是否已满
                    long num = this.countUserNumByTeamId(team.getId());
                    if (num >= team.getMaxNum()) {
                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍已满");
                    }
                    // 不能重复加入已加入的队伍
                    QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
                    userTeamQueryWrapper.eq("teamId", teamJoinRequest.getTeamId());
                    userTeamQueryWrapper.eq("userId", loginUser.getId());
                    long count1 = userTeamService.count(userTeamQueryWrapper);
                    if (count1 > 0) {
                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "不能重复加入队伍");
                    }
                    // 校验通过，向关联表添加数据
                    UserTeam userTeam = new UserTeam();
                    userTeam.setUserId(loginUser.getId());
                    userTeam.setTeamId(teamJoinRequest.getTeamId());
                    userTeam.setJoinTime(new Date());
                    return userTeamService.save(userTeam);
                }
            }
        } catch (InterruptedException e) {
            log.error("tryLock error", e);
            return false;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 根据队伍 id查询队伍的人数
     *
     * @param id 队伍的 id
     * @return 该队伍的人数
     */
    private long countUserNumByTeamId(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        QueryWrapper<UserTeam> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("teamId", id);
        return userTeamService.count(queryWrapper);
    }

    /**
     * 退出（解散）队伍
     *
     * @param teamQuitRequest 封装的请求参数类
     * @param loginUser       当前登录用户
     * @return true操作成功，false操作失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)  // 涉及多次数据库删除操作，需要事务
    public boolean quitTeam(TeamQuitRequest teamQuitRequest, User loginUser) {
        Long teamId = teamQuitRequest.getTeamId();
        Long userId = loginUser.getId();
        // 校验有没有加入队伍，没有加入队伍无需退出
        QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
        userTeamQueryWrapper.eq("userId", userId);
        userTeamQueryWrapper.eq("teamId", teamId);
        long count = userTeamService.count(userTeamQueryWrapper);
        if (count == 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "未加入该队伍");
        }
        // 判断队伍剩余的人数
        long num = this.countUserNumByTeamId(teamId);
        Team team = this.getTeamById(teamId);
        if (num == 1) {
            // 队伍只剩一人，删除关联表信息，并直接解散队伍
            QueryWrapper<UserTeam> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("userId", userId);
            queryWrapper.eq("teamId", teamId);
            userTeamService.remove(queryWrapper);  // 删除关联表信息
            this.removeById(teamId);  // 删除队伍
            return true;
        } else if (num >= 2) {
            // 队伍还有至少两人
            // 判断退出的是不是队长
            if (userId.equals(team.getUserId())) {
                // 是队长，队长转移，删除信息
                // 查询队伍中加入时间最早的成员，将队长转移给他
                QueryWrapper<UserTeam> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("teamId", teamId);
                queryWrapper.last("order by joinTime asc limit 2");
                List<UserTeam> userTeamList = userTeamService.list(queryWrapper);
                if (userTeamList == null || userTeamList.size() < 2) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR);
                }
                Long nextLeaderId = userTeamList.get(1).getUserId();
                // 更新队长
                Team updateTeam = new Team();
                updateTeam.setUserId(nextLeaderId);
                updateTeam.setId(teamId);
                boolean b = this.updateById(updateTeam);
                if (!b) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新队长失败");
                }
                // 删除信息
                QueryWrapper<UserTeam> userTeamQueryWrapper1 = new QueryWrapper<>();
                userTeamQueryWrapper1.eq("userId", userId);
                userTeamQueryWrapper1.eq("teamId", teamId);
                return userTeamService.remove(userTeamQueryWrapper1);
            } else {
                // 不是队长，直接删除信息
                QueryWrapper<UserTeam> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("userId", userId);
                queryWrapper.eq("teamId", teamId);
                return userTeamService.remove(queryWrapper);  // 删除关联表信息
            }
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR);
    }

    /**
     * 通过id获取team
     */
    public Team getTeamById(long teamId) {
        if (teamId <= 0) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "队伍不存在");
        }
        // 从数据库中获取
        Team team = this.getById(teamId);
        if (team == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "队伍不存在");
        }
        return team;
    }

}




