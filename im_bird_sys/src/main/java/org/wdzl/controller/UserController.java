package org.wdzl.controller;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.wdzl.bo.UserBO;
import org.wdzl.enums.OperatorFriendRequestTypeEnum;
import org.wdzl.enums.SearchFriendsStatusEnum;
import org.wdzl.pojo.ChatMsg;
import org.wdzl.pojo.FriendsRequest;
import org.wdzl.pojo.User;
import org.wdzl.services.UserServices;
import org.wdzl.utils.FastDFSClient;
import org.wdzl.utils.FileUtils;
import org.wdzl.utils.IWdzlJSONResult;
import org.wdzl.utils.MD5Utils;
import org.wdzl.vo.FriendsRequestVO;
import org.wdzl.vo.MyFriendsVO;
import org.wdzl.vo.UserVo;
import java.util.List;


@Controller
@RequestMapping("/user")
public class UserController {

    @Autowired
    UserServices userServices;

    @Autowired
    FastDFSClient fastDFSClient;

    @RequestMapping("/registerOrLogin")
    @ResponseBody
    //用户登录与注册一体化方法
    public IWdzlJSONResult registerOrlogin(User user){
        User userResult = userServices.queryUserNameIsExit(user.getUsername());
        if(userResult!=null){//此用户存在，可登录
            if(!userResult.getPassword().equals(MD5Utils.getPwd(user.getPassword()))){
                return IWdzlJSONResult.errorMsg("密码不正确");
            }
        }else{//注册
            user.setNickname(user.getUsername());
            user.setQrcode("");
            user.setPassword(MD5Utils.getPwd(user.getPassword()));
            user.setFaceImage("");
            user.setFaceImageBig("");
            userResult = userServices.insert(user);
        }
        UserVo userVo = new UserVo();
        BeanUtils.copyProperties(userResult,userVo);
        return IWdzlJSONResult.ok(userVo);
    }
    @RequestMapping("/uploadFaceBase64")
    @ResponseBody
    //用户头像上传访问方法
    public IWdzlJSONResult uploadFaceBase64(@RequestBody UserBO userBO) throws Exception {
        //获取前端传过来的base64的字符串，然后转为文件对象在进行上传
        String base64Data = userBO.getFaceData();
        String userFacePath = "/usr/local/face/"+userBO.getUserId()+"userFaceBase64.png";
        //调用FileUtils 类中的方法将base64 字符串转为文件对象
        FileUtils.base64ToFile(userFacePath, base64Data);
        MultipartFile multipartFile = FileUtils.fileToMultipart(userFacePath);
        //获取fastDFS上传图片的路径
        String url = fastDFSClient.uploadBase64(multipartFile);
        System.out.println(url);
        String thump = "_150x150.";
        String[] arr = url.split("\\.");
        String thumpImgUrl = arr[0]+thump+arr[1];
//        String bigFace = "dssdklsdjsdj3498458.png";
//        String thumpFace = "dssdklsdjsdj3498458_150x150.png";
        //更新用户头像
        User user = new User();
        user.setId(userBO.getUserId());
        user.setFaceImage(thumpImgUrl);
        user.setFaceImageBig(url);
        User result = userServices.updateUserInfo(user);
        return  IWdzlJSONResult.ok(result);
    }

    //修改昵称方法
    @RequestMapping("/setNickname")
    @ResponseBody
    public IWdzlJSONResult setNickName(User user){
        User userResult = userServices.updateUserInfo(user);
        return IWdzlJSONResult.ok(userResult);
    }
    //搜索好友的请求方法
    @RequestMapping("/searchFriend")
    @ResponseBody
    public IWdzlJSONResult searchFriend(String myUserId,String friendUserName){
        /**
         * 前置条件：
         * 1.搜索的用户如果不存在，则返回【无此用户】
         * 2.搜索的账号如果是你自己，则返回【不能添加自己】
         * 3.搜索的朋友已经是你好友，返回【该用户已经是你的好友】
         */
        Integer status = userServices.preconditionSearchFriends(myUserId,friendUserName);
        if(status==SearchFriendsStatusEnum.SUCCESS.status){
            User user = userServices.queryUserNameIsExit(friendUserName);
            UserVo userVo = new UserVo();
            BeanUtils.copyProperties(user,userVo);
            return IWdzlJSONResult.ok(userVo);
        }else{
            String msg = SearchFriendsStatusEnum.getMsgByKey(status);
            return IWdzlJSONResult.errorMsg(msg);
        }
    }
    //发送添加好友请求的方法
    @RequestMapping("/addFriendRequest")
    @ResponseBody
    public IWdzlJSONResult addFriendRequest(String myUserId,String friendUserName){
        if(StringUtils.isBlank(myUserId)|| StringUtils.isBlank(friendUserName)){
            return IWdzlJSONResult.errorMsg("好友信息为空");
        }

        /**
         * 前置条件：
         * 1.搜索的用户如果不存在，则返回【无此用户】
         * 2.搜索的账号如果是你自己，则返回【不能添加自己】
         * 3.搜索的朋友已经是你好友，返回【该用户已经是你的好友】
         */
        Integer status = userServices.preconditionSearchFriends(myUserId,friendUserName);
        if(status==SearchFriendsStatusEnum.SUCCESS.status){
            userServices.sendFriendRequest(myUserId,friendUserName);
        }else{
            String msg = SearchFriendsStatusEnum.getMsgByKey(status);
            return IWdzlJSONResult.errorMsg(msg);
        }
        return IWdzlJSONResult.ok();
    }
    //好友请求列表查询
    @RequestMapping("/queryFriendRequest")
    @ResponseBody
    public IWdzlJSONResult queryFriendRequest(String userId){
        List<FriendsRequestVO> friendRequestList = userServices.queryFriendRequestList(userId);
        return IWdzlJSONResult.ok(friendRequestList);
    }

    //好友请求处理映射my_friends
    @RequestMapping("/operFriendRequest")
    @ResponseBody
    public IWdzlJSONResult operFriendRequest(String acceptUserId,String sendUserId,Integer operType){
        FriendsRequest friendsRequest = new FriendsRequest();
        friendsRequest.setAcceptUserId(acceptUserId);
        friendsRequest.setSendUserId(sendUserId);
        if(operType== OperatorFriendRequestTypeEnum.IGNORE.type){
            //满足此条件将需要对好友请求表中的数据进行删除操作
            userServices.deleteFriendRequest(friendsRequest);
        }else if(operType==OperatorFriendRequestTypeEnum.PASS.type){
            //满足此条件表示需要向好友表中添加一条记录，同时删除好友请求表中对应的记录
            userServices.passFriendRequest(sendUserId,acceptUserId);
        }
        //查询好友表中的列表数据
        List<MyFriendsVO> myFriends = userServices.queryMyFriends(acceptUserId);
        return IWdzlJSONResult.ok(myFriends);
    }

    /**
     * 好友列表查询
     * @param userId
     * @return
     */
    @RequestMapping("/myFriends")
    @ResponseBody
    public IWdzlJSONResult myFriends(String userId){
        if (StringUtils.isBlank(userId)){
            return IWdzlJSONResult.errorMsg("用户id为空");
        }
        //数据库查询好友列表
        List<MyFriendsVO> myFriends = userServices.queryMyFriends(userId);
        return IWdzlJSONResult.ok(myFriends);
    }

    @RequestMapping("/getUser")
    public String getUserById(String id, Model model){
        User user = userServices.getUserById(id);
        model.addAttribute("user",user);
        return "user_list";
    }

    /**
     * 用户手机端获取未签收的消息列表
     * @param acceptUserId
     * @return
     */
    @RequestMapping("/getUnReadMsgList")
    @ResponseBody
    public IWdzlJSONResult getUnReadMsgList(String acceptUserId){
        if(StringUtils.isBlank(acceptUserId)){
            return IWdzlJSONResult.errorMsg("接收者ID不能为空");
        }
        //根据接收ID查找为签收的消息列表
        List<ChatMsg> unReadMsgList = userServices.getUnReadMsgList(acceptUserId);
        return IWdzlJSONResult.ok(unReadMsgList);

    }
}













