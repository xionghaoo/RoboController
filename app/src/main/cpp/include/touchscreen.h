#ifndef TOUCHSCREEN_H
#define TOUCHSCREEN_H
#include <string>
#include <vector>
#include "opencv/highgui.h"
class  MarkPoint
{
public:
    MarkPoint(){}
    MarkPoint(float  fx, float fy){ m_xPoint = fx; m_yPoint = fy;}
    virtual ~MarkPoint(){}
    float m_xPoint{0.0f};
    float m_yPoint{0.0f};
};

class  InTouchParam
{
public:
    InTouchParam(){}
    virtual ~InTouchParam(){}

    int m_pxWidth{0};
    int m_pxHeight{0};

    std::string                      m_dataPath;
    std::vector<MarkPoint>           m_markPoints;
    std::function<void( int , int )> m_markCallBack{nullptr};
    std::function<void( int , int , int , bool)> m_logCallBack{nullptr};
};

/*
 * param : 传入参数
*/
long   InitTouchScreen( InTouchParam& param );

/*
 * index : 标定点序号
 * mode : 标定模式
 *
 * 回调返回标定结果
 *
*/
int    ProcessMarking( int index ,  cv::Mat & frame);


/*
 *
 *回调返回结果
 *
*/
int    PorcessTouchData(cv::Mat & frame);

/*
*设置掩码模板，用于支持一些特殊点击事件
*x:0,y:0,width:0,height:0为清空该mask缓存
*/
int   GenMask(int x, int y, int width, int height);

/*
*清空标定缓存
*/
int    ClearMarking();

/*
*设置当前使用的模式
*/
int    SetCurMode(int mode); // 0-停止触屏工作模式 ,1-启动触屏标定工作模式,2-启动触屏识别模式


void   ExitTouchScreen();


#endif // TOUCHSCREEN_H
