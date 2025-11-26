package com.arteon.service;

import com.arteon.domain.Team;
import com.arteon.domain.User;
import com.arteon.domain.dto.TeamQuery;
import com.arteon.domain.request.TeamJoinRequest;
import com.arteon.domain.request.TeamQuitRequest;
import com.arteon.domain.request.TeamUpdateRequest;
import com.arteon.domain.vo.TeamVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface TeamService extends IService<Team> {

    long addTeam(Team team, User loginUser);

    boolean updateTeam(TeamUpdateRequest teamUpdateRequest, User loginUser);

    boolean deleteTeam(long id, User loginUser);

    List<TeamVO> listTeam(TeamQuery teamQuery, User loginUser);

    List<TeamVO> myCreateTeamList(Long id);

    List<TeamVO> myJoinTeamList(User loginUser);

    boolean joinTeam(TeamJoinRequest teamJoinRequest, User loginUser);

    boolean quitTeam(TeamQuitRequest teamQuitRequest, User loginUser);

}
