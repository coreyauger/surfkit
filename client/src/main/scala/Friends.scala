package io.surfkit.client

import io.surfkit.model.Auth
import japgolly.scalajs.react.ReactComponentB
import japgolly.scalajs.react.vdom.prefix_<^._

trait Friends {

}

object Friends{
  import io.surfkit.client.Base._

  type FriendSelectEvent = (Auth.ProfileInfo) => Unit
  type FriendAddEvent = (Set[Auth.ProfileInfo]) => Unit

  case class FriendsState(friends:Seq[Auth.ProfileInfo], filter:String)


  val FriendCard = ReactComponentB[(Auth.ProfileInfo, FriendSelectEvent)]("FriendCard")
    .render(props => {
      val (friend,onSelect) = props
      <.div(^.className:="friend-card",^.onClick --> onSelect(friend),
        <.img(^.src:= friend.avatarUrl, ^.className:="avatar"),
        <.span(friend.fullName)
      )
    })
    .build

  val FriendList = ReactComponentB[(FriendsState, ReactEvent, FriendSelectEvent)]("FriendList")
    .render(props => {
      val (friends,onFilterChange, onFriendSelect) = props
      val name = friends.filter.toLowerCase
      <.div(^.className:="friend-list",
        <.header(^.className:="tools",
          SearchBar( (friends.filter,onFilterChange, null,"fa fa-search") )
        ),
        <.div(friends.friends.filter(_.fullName.toLowerCase.contains(name)).map(f => FriendCard( (f,onFriendSelect) ) ))
      )
    })
    .build


  val FriendSelector = ReactComponentB[(FriendsState, Set[Auth.ProfileInfo], ReactEvent, FriendAddEvent)]("FriendFinder")
    .render(props => {
      val (friends,m,onFilterChange, onFriendsAdd) = props
      var members = m
      val name = friends.filter.toLowerCase
      val friendList =
        if(name.length > 0)
          friends.friends.filter(_.fullName.toLowerCase.contains(name))
        else
          Nil
      <.div(^.className:="friend-list",
        <.header(^.className:="tools",
          SearchBar( (friends.filter,onFilterChange, (i:String) =>{
            onFriendsAdd(members)
          },"fa fa-plus") )
        ),
        <.div(friendList.map(f => FriendCard( (f,(friend:Auth.ProfileInfo)=>{
          members =  members + friend
        }) ) ))
      )
    })
    .build

}
