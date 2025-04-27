/* groups */
INSERT INTO groups (id, invite_token, display_name)
    VALUES ('458a3a93-5e87-42df-bbc7-2bf47247798c', 'invite_token_for_test_group', 'Test Group');
INSERT INTO groups (id, invite_token, display_name)
    VALUES ('cfff292e-642a-47d0-b24c-c8ff62a3c490', 'invite_token_for_another_group', 'Another Group');

/* users */
INSERT INTO users (id, last_login)
    VALUES ('user1', now());
INSERT INTO active_users (id, display_name, profile_picture_url)
    VALUES ('user1', 'User1', NULL);
INSERT INTO refresh_tokens (user_id, token)
    VALUES ('user1', 'digest$$WxFhjC5EAnh30M0JIe0Wa58Xb1BYf8kedTTdKUbbd9Y='); -- RefreshToken(UserId("user1"), "secret1")

INSERT INTO users (id, last_login)
    VALUES ('user2', now());
INSERT INTO active_users (id, display_name, profile_picture_url)
    VALUES ('user2', 'User2', NULL);
INSERT INTO refresh_tokens (user_id, token)
    VALUES ('user2', 'digest$$NSJNDTRl106FX41poTbnnHROo1pnXTOTNgoyfL9jWaI='); -- RefreshToken(UserId("user2"), "secret2")

INSERT INTO users (id, last_login)
    VALUES ('user3', now());
INSERT INTO active_users (id, display_name, profile_picture_url)
    VALUES ('user3', 'User3', NULL);

INSERT INTO users (id, last_login)
    VALUES ('user4', now());
INSERT INTO active_users (id, display_name, profile_picture_url)
    VALUES ('user4', 'User4', NULL);

INSERT INTO users (id, last_login)
    VALUES ('user5', now());
INSERT INTO active_users (id, display_name, profile_picture_url)
    VALUES ('user5', 'User5', NULL);

/* memberships */
INSERT INTO membership (user_id, group_id)
    VALUES ('user1', '458a3a93-5e87-42df-bbc7-2bf47247798c');
INSERT INTO membership (user_id, group_id)
    VALUES ('user2', '458a3a93-5e87-42df-bbc7-2bf47247798c');
INSERT INTO membership (user_id, group_id)
    VALUES ('user2', 'cfff292e-642a-47d0-b24c-c8ff62a3c490');
INSERT INTO membership (user_id, group_id)
    VALUES ('user3', '458a3a93-5e87-42df-bbc7-2bf47247798c');
INSERT INTO membership (user_id, group_id)
    VALUES ('user4', '458a3a93-5e87-42df-bbc7-2bf47247798c');
INSERT INTO membership (user_id, group_id)
    VALUES ('user5', '458a3a93-5e87-42df-bbc7-2bf47247798c');

/* transactions */
INSERT INTO transactions (id, group_id, originating_user, name, expense_total, timestamp)
    VALUES ('206aa74e-914b-40a4-a72b-df1fd7280353', '458a3a93-5e87-42df-bbc7-2bf47247798c', 'user1', 'Some Expense', 20, now());
INSERT INTO balance_changes (transaction_id, user_id, amount)
    VALUES ('206aa74e-914b-40a4-a72b-df1fd7280353', 'user3', -10);
INSERT INTO balance_changes (transaction_id, user_id, amount)
    VALUES ('206aa74e-914b-40a4-a72b-df1fd7280353', 'user2', 10);
